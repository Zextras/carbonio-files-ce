// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.files.rest.services;

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.zextras.carbonio.files.Files.Db.RootId;
import com.zextras.carbonio.files.config.FilesConfig;
import com.zextras.carbonio.files.dal.dao.User;
import com.zextras.carbonio.files.dal.dao.ebean.ACL.SharePermission;
import com.zextras.carbonio.files.dal.dao.ebean.FileVersion;
import com.zextras.carbonio.files.dal.dao.ebean.Node;
import com.zextras.carbonio.files.dal.dao.ebean.NodeType;
import com.zextras.carbonio.files.dal.repositories.interfaces.FileVersionRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.LinkRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.NodeRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.ShareRepository;
import com.zextras.carbonio.files.exceptions.NodeNotFoundException;
import com.zextras.carbonio.files.exceptions.NodePermissionException;
import com.zextras.carbonio.files.netty.utilities.BufferInputStream;
import com.zextras.carbonio.files.rest.types.BlobResponse;
import com.zextras.carbonio.files.utilities.MimeTypeUtils;
import com.zextras.carbonio.files.utilities.PermissionsChecker;
import com.zextras.carbonio.usermanagement.exceptions.BadRequest;
import com.zextras.filestore.api.UploadResponse;
import com.zextras.filestore.model.FilesIdentifier;
import com.zextras.storages.api.StoragesClient;
import io.vavr.control.Try;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public class BlobService {

  private final NodeRepository        nodeRepository;
  private final FileVersionRepository fileVersionRepository;
  private final ShareRepository       shareRepository;
  private final LinkRepository        linkRepository;
  private final PermissionsChecker    permissionsChecker;
  private final MimeTypeUtils         mimeTypeUtils;
  private final String                storageUrl;

  @Inject
  public BlobService(
    NodeRepository nodeRepository,
    FileVersionRepository fileVersionRepository,
    ShareRepository shareRepository,
    LinkRepository linkRepository,
    PermissionsChecker permissionsChecker,
    FilesConfig filesConfig,
    MimeTypeUtils mimeTypeUtils
  ) {
    this.nodeRepository = nodeRepository;
    this.fileVersionRepository = fileVersionRepository;
    this.shareRepository = shareRepository;
    this.linkRepository = linkRepository;
    this.permissionsChecker = permissionsChecker;
    this.mimeTypeUtils = mimeTypeUtils;
    Properties p = filesConfig.getProperties();
    storageUrl = "http://"
      + p.getProperty("carbonio.storage.url")
      + ":" + p.getProperty("carbonio.storage.port")
      + "/";
  }

  public Try<BlobResponse> downloadFile(
    String nodeId,
    Optional<Integer> version,
    User requester
  ) {
    return nodeRepository
      .getNode(nodeId)
      .map(node -> fileVersionRepository
        .getFileVersion(node.getId(), version.orElse(node.getCurrentVersion()))
        .map(fileVersion -> Try
          .of(() -> StoragesClient
            .atUrl(storageUrl)
            .download(FilesIdentifier.of(
              fileVersion.getNodeId(),
              fileVersion.getVersion(),
              node.getOwnerId())
            )
          )
          .map((blob) -> new BlobResponse(
            blob,
            node.getFullName(),
            fileVersion.getSize(),
            fileVersion.getMimeType())
          )
        ).orElseGet(() -> Try.failure(new NodeNotFoundException()))
      ).orElseGet(() -> Try.failure(new NodeNotFoundException()));
  }

  public Try<BlobResponse> downloadFileByLink(String linkId) {
    return linkRepository.getLinkByPublicId(linkId)
      .map(link -> nodeRepository.getNode(link.getNodeId()))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(node -> fileVersionRepository.getFileVersion(node.getId(), node.getCurrentVersion())
        .map(fileVersion -> Try
          .of(() -> StoragesClient
            .atUrl(storageUrl)
            .download(FilesIdentifier.of(
              fileVersion.getNodeId(),
              fileVersion.getVersion(),
              node.getOwnerId())
            )
          )
          .map(blob -> new BlobResponse(
            blob,
            node.getFullName(),
            fileVersion.getSize(),
            fileVersion.getMimeType())
          )
        ).orElseGet(() -> Try.failure(new NodeNotFoundException()))
      ).orElseGet(() -> Try.failure(new NodeNotFoundException()));
  }

  public Try<String> uploadFile(
    User requester,
    BufferInputStream bufferInputStream,
    String folderId,
    String filename,
    String description
  ) {

    if (permissionsChecker
      .getPermissions(folderId, requester.getUuid())
      .has(SharePermission.READ_AND_WRITE)
    ) {

      String nodeId = UUID.randomUUID().toString();
      String nodeOwner = folderId.equals(RootId.LOCAL_ROOT)
        ? requester.getUuid()
        : nodeRepository.getNode(folderId)
          .map(Node::getOwnerId)
          .orElse(requester.getUuid());

      MediaType mediaType = mimeTypeUtils.detectMimeTypeFromFilename(
        filename,
        MediaType.OCTET_STREAM.toString()
      );

      NodeType nodeType = NodeType.getNodeType(mediaType);

      System.out.println("attributes read: " + filename);
      UploadResponse uploadResponse = null;
      try {
        uploadResponse = StoragesClient
          .atUrl(storageUrl)
          .uploadPost(FilesIdentifier.of(nodeId, 1, requester.getUuid()), bufferInputStream);

        System.out.println(uploadResponse.getDigest());
        System.out.println(uploadResponse.getSize());
        System.out.println(nodeId);
      } catch (Exception e) {
        e.printStackTrace();
      }

      Node folder = nodeRepository.getNode(folderId).get();
      nodeRepository.createNewNode(
        nodeId,
        requester.getUuid(),
        nodeOwner,
        folderId,
        searchAlternativeName(filename.trim(), folderId, nodeOwner),
        description,
        nodeType,
        folder.getNodeType().equals(NodeType.ROOT)
          ? folderId
          : folder.getAncestorIds() + "," + folderId,
        uploadResponse.getSize()
      );

      fileVersionRepository.createNewFileVersion(
        nodeId,
        requester.getUuid(),
        1,
        mediaType.toString(),
        uploadResponse.getSize(),
        uploadResponse.getDigest(),
        false
      );

      // Add new shares for the new file
      // Create share also for the requester if it is not the owner of the parent folder
      shareRepository
        .getShares(folderId, Collections.emptyList())
        .forEach(share -> shareRepository.upsertShare(
            nodeId,
            share.getTargetUserId(),
            share.getPermissions(),
            false,
            share.getExpiredAt()
          )
        );

      return Try.success(nodeId);
    }

    return Try.failure(new NodePermissionException());
  }

  public Try<Integer> uploadFileVersion(
    User requester,
    BufferInputStream bufferInputStream,
    String nodeId,
    String filename,
    boolean overwrite
  ) {

    if (permissionsChecker
      .getPermissions(nodeId, requester.getUuid())
      .has(SharePermission.READ_AND_WRITE)
    ) {

      Optional<Node> node = nodeRepository.getNode(nodeId);
      if (!node.isPresent()) {
        return Try.failure(new NodeNotFoundException());
      }

      int newVersion = overwrite
        ? node.get().getCurrentVersion()
        : node.get().getCurrentVersion() + 1;

      UploadResponse uploadResponse = null;
      try {
        if (overwrite) {
          uploadResponse = StoragesClient
            .atUrl(storageUrl)
            .uploadPut(
              FilesIdentifier.of(nodeId, newVersion, requester.getUuid()),
              bufferInputStream
            );

        } else {
          uploadResponse = StoragesClient
            .atUrl(storageUrl)
            .uploadPost(
              FilesIdentifier.of(nodeId, newVersion, requester.getUuid()),
              bufferInputStream
            );
        }

        System.out.println(uploadResponse.getDigest());
        System.out.println(uploadResponse.getSize());
        System.out.println(nodeId);
      } catch (Exception e) {
        e.printStackTrace();
      }

      MediaType mediaType = mimeTypeUtils.detectMimeTypeFromFilename(
        filename,
        MediaType.OCTET_STREAM.toString()
      );

      NodeType nodeType = NodeType.getNodeType(mediaType);

      if (nodeType != node.get().getNodeType()) {
        return Try.failure(new BadRequest());
      }

      Node updatedNode = node.get();
      if (overwrite) {
        FileVersion fileVersionOld = fileVersionRepository.getFileVersion(nodeId, newVersion).get();
        fileVersionRepository.deleteFileVersion(fileVersionOld);
      } else {
        updatedNode.setCurrentVersion(newVersion);
      }

      fileVersionRepository.createNewFileVersion(
        nodeId,
        requester.getUuid(),
        newVersion,
        mediaType.toString(),
        uploadResponse.getSize(),
        uploadResponse.getDigest(),
        false
      );
      updatedNode.setSize(uploadResponse.getSize());
      updatedNode.setLastEditorId(requester.getUuid());
      nodeRepository.updateNode(updatedNode);

      return Try.success(newVersion);
    }

    return Try.failure(new NodePermissionException());
  }

  /**
   * This method is used to search an alternative name if the filename is already present in the
   * destination folder.
   *
   * @param filename a {@link String} representing the filename of the node I have to upload.
   * @param destinationFolderId is a {@link String } representing the id of the destination folder.
   *
   * @return a {@link String} of the alternative name if the filename is already taken or the chosen
   * filename.
   */
  private String searchAlternativeName(
    String filename,
    String destinationFolderId,
    String nodeOwner
  ) {
    int level = 1;
    String finalFilename = filename;
    while (nodeRepository
      .getNodeByName(finalFilename, destinationFolderId, nodeOwner)
      .isPresent()
    ) {
      int dotPosition = filename.lastIndexOf('.');
      finalFilename = (dotPosition != -1)
        ? filename.substring(0, dotPosition) + " (" + level + ")" + filename.substring(dotPosition)
        : filename + " (" + level + ")";
      ++level;
    }
    return finalFilename;
  }
}