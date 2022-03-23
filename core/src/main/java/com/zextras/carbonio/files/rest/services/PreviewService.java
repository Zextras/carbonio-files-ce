// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.files.rest.services;

import com.google.inject.Inject;
import com.zextras.carbonio.files.Files.Config.Preview;
import com.zextras.carbonio.files.config.FilesConfig;
import com.zextras.carbonio.files.dal.dao.ebean.ACL.SharePermission;
import com.zextras.carbonio.files.dal.dao.ebean.FileVersion;
import com.zextras.carbonio.files.dal.repositories.interfaces.FileVersionRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.NodeRepository;
import com.zextras.carbonio.files.exceptions.NodeNotFoundException;
import com.zextras.carbonio.files.rest.types.BlobResponse;
import com.zextras.carbonio.files.rest.types.PreviewQueryParameters;
import com.zextras.carbonio.files.utilities.MimeTypeUtils;
import com.zextras.carbonio.files.utilities.PermissionsChecker;
import com.zextras.carbonio.preview.PreviewClient;
import com.zextras.carbonio.preview.queries.Query;
import com.zextras.carbonio.preview.queries.Query.QueryBuilder;
import com.zextras.carbonio.preview.queries.enums.ServiceType;
import com.zextras.carbonio.usermanagement.exceptions.BadRequest;
import io.vavr.control.Try;
import java.util.Optional;
import java.util.Properties;

public class PreviewService {

  private final PermissionsChecker    permissionsChecker;
  private final NodeRepository        nodeRepository;
  private final FileVersionRepository fileVersionRepository;
  private final MimeTypeUtils         mimeTypeUtils;
  private final String                previewURL;

  @Inject
  public PreviewService(
    FilesConfig filesConfig,
    PermissionsChecker permissionsChecker,
    NodeRepository nodeRepository,
    FileVersionRepository fileVersionRepository,
    MimeTypeUtils mimeTypeUtils
  ) {
    this.permissionsChecker = permissionsChecker;
    this.nodeRepository = nodeRepository;
    this.fileVersionRepository = fileVersionRepository;
    this.mimeTypeUtils = mimeTypeUtils;

    Properties properties = filesConfig.getProperties();
    previewURL = properties.getProperty(Preview.PROTOCOL, "http")
      + "://"
      + properties.getProperty(Preview.URL, "127.78.0.2")
      + ":"
      + properties.getProperty(Preview.PORT, "20003");
  }

  /**
   * <p>This method fetch the preview of an image if the requester has read rights.
   *
   * @param requesterId is a {@link String} representing the id of the requester.
   * @param nodeId is a {@link String} representing the id of the file to fetch.
   * @param version is a <code> integer </code> representing the version of the file to fetch.
   * @param area is a {@link String} containing the area (widthxheight) of the preview.
   * @param queryParameters is a {@link PreviewQueryParameters} containing all the query
   * parameters.
   *
   * @return fetched {@link BlobResponse} wrapped in a {@link Try} that can be successful or failed
   */
  public Try<BlobResponse> getPreviewOfImage(
    String requesterId,
    String nodeId,
    int version,
    String area,
    PreviewQueryParameters queryParameters
  ) {
    Try<Boolean> tryCheck = checkNodePermissionAndExistence(requesterId, nodeId, version, "image/");

    if (tryCheck.isSuccess()) {
      Query query = generateQuery(nodeId, version, Optional.of(area), queryParameters);

      Try<com.zextras.carbonio.preview.queries.BlobResponse> response = PreviewClient
        .atURL(previewURL)
        .getPreviewOfImage(query);

      return mapResponseToBlobResponse(response, nodeId);
    }
    return Try.failure(tryCheck.failed().get());
  }

  /**
   * <p>This method fetch the thumbnail of an image if the requester has read rights.
   *
   * @param requesterId is a {@link String} representing the id of the requester.
   * @param nodeId is a {@link String} representing the id of the file to fetch.
   * @param version is a <code> integer </code> representing the version of the file to fetch.
   * @param area is a {@link String} containing the area (widthxheight) of the preview.
   * @param queryParameters is a {@link PreviewQueryParameters} containing all the query
   * parameters.
   *
   * @return fetched {@link BlobResponse} wrapped in a {@link Try} that can be successful or failed
   */
  public Try<BlobResponse> getThumbnailOfImage(
    String requesterId,
    String nodeId,
    int version,
    String area,
    PreviewQueryParameters queryParameters
  ) {
    Try<Boolean> check = checkNodePermissionAndExistence(requesterId, nodeId, version, "image/");

    if (check.isSuccess()) {
      Query query = generateQuery(nodeId, version, Optional.of(area), queryParameters);

      Try<com.zextras.carbonio.preview.queries.BlobResponse> response = PreviewClient
        .atURL(previewURL)
        .getThumbnailOfImage(query);

      return mapResponseToBlobResponse(response, nodeId);
    }
    return Try.failure(check.failed().get());
  }

  /**
   * <p>This method fetch the preview of a pdf if the requester has read rights.
   *
   * @param requesterId is a {@link String} representing the id of the requester.
   * @param nodeId is a {@link String} representing the id of the file to fetch.
   * @param version is a <code> integer </code> representing the version of the file to fetch.
   * @param queryParameters is a {@link PreviewQueryParameters} containing all the query
   * parameters.
   *
   * @return fetched {@link BlobResponse} wrapped in a {@link Try} that can be successful or failed
   */
  public Try<BlobResponse> getPreviewOfPdf(
    String requesterId,
    String nodeId,
    int version,
    PreviewQueryParameters queryParameters
  ) {
    Try<Boolean> tryCheck = checkNodePermissionAndExistence(requesterId, nodeId, version,
      "application/pdf");

    if (tryCheck.isSuccess()) {
      Query query = generateQuery(nodeId, version, Optional.empty(), queryParameters);

      Try<com.zextras.carbonio.preview.queries.BlobResponse> response = PreviewClient
        .atURL(previewURL)
        .getPreviewOfPdf(query);

      return mapResponseToBlobResponse(response, nodeId);
    }
    return Try.failure(tryCheck.failed().get());
  }

  /**
   * <p>This method fetch the thumbnail of a pdf if the requester has read rights.
   *
   * @param requesterId is a {@link String} representing the id of the requester.
   * @param nodeId is a {@link String} representing the id of the file to fetch.
   * @param version is a <code> integer </code> representing the version of the file to fetch.
   * @param area is a {@link String} containing the area (widthxheight) of the preview.
   * @param queryParameters is a {@link PreviewQueryParameters} containing all the query
   * parameters.
   *
   * @return fetched {@link BlobResponse} wrapped in a {@link Try} that can be successful or failed
   */
  public Try<BlobResponse> getThumbnailOfPdf(
    String requesterId,
    String nodeId,
    int version,
    String area,
    PreviewQueryParameters queryParameters
  ) {
    Try<Boolean> tryCheck = checkNodePermissionAndExistence(
      requesterId,
      nodeId,
      version,
      "application/pdf"
    );

    if (tryCheck.isSuccess()) {
      Query query = generateQuery(nodeId, version, Optional.of(area), queryParameters);

      Try<com.zextras.carbonio.preview.queries.BlobResponse> response = PreviewClient
        .atURL(previewURL)
        .getThumbnailOfPdf(query);

      return mapResponseToBlobResponse(response, nodeId);
    }
    return Try.failure(tryCheck.failed().get());
  }

  /**
   * <p>This checks if the requested node can be accessed by the requester
   * and if the node mimetype is supported by the system.
   *
   * @param requesterId is a {@link String} representing the id of the requester.
   * @param nodeId is a {@link String } representing the nodeId of the node.
   * @param version is a <code> integer </code> representing the version of the node.
   * @param allowedMimeType is a {@link String} representing the allowed list or instance of
   * mimetype that the calling methods allow (for instance a method may want only mimetype that are
   * of "image" so "image/something" while another method "application" so "application/something"
   *
   * @return a {@link Try} with on success True or on failure the specific error
   */
  private Try<Boolean> checkNodePermissionAndExistence(
    String requesterId,
    String nodeId,
    int version,
    String allowedMimeType
  ) {
    Optional<FileVersion> optFileVersion = fileVersionRepository.getFileVersion(nodeId, version);
    if (permissionsChecker.getPermissions(nodeId, requesterId).has(SharePermission.READ_ONLY)
      && optFileVersion.isPresent()
    ) {
      return (mimeTypeUtils.isMimeTypeAllowed(optFileVersion.get().getMimeType(), allowedMimeType))
        ? Try.success(true)
        : Try.failure(new BadRequest());
    }
    return Try.failure(new NodeNotFoundException());
  }

  /**
   * <p>This method generates a valid query object.
   *
   * @param nodeId is a {@link String} representing the id of the file to fetch.
   * @param version is a <code> integer </code>the integer representing the version of the file to
   * fetch.
   * @param optArea is a {@link Optional} containing the area (widthxheight) of the preview.
   * @param queryParameters is a {@link PreviewQueryParameters} containing all the query
   * parameters.
   *
   * @return generated {@link Query}
   */
  private Query generateQuery(
    String nodeId,
    int version,
    Optional<String> optArea,
    PreviewQueryParameters queryParameters
  ) {
    QueryBuilder parameterBuilder = new QueryBuilder()
      .setServiceType(ServiceType.FILES)
      .setNodeId(nodeId)
      .setVersion(version);

    optArea.ifPresent(parameterBuilder::setPreviewArea);
    queryParameters.getQuality().ifPresent(parameterBuilder::setQuality);
    queryParameters.getOutputFormat().ifPresent(parameterBuilder::setOutputFormat);
    queryParameters.getCrop().ifPresent(parameterBuilder::setCrop);
    queryParameters.getShape().ifPresent(parameterBuilder::setShape);
    queryParameters.getFirstPage().ifPresent(parameterBuilder::setFirstPage);
    queryParameters.getLastPage().ifPresent(parameterBuilder::setLastPage);

    return parameterBuilder.build();
  }

  /**
   * <p>This method maps all fields of a {@link com.zextras.carbonio.preview.queries.BlobResponse}
   * object to the corresponding fields of a {@link BlobResponse} object.
   *
   * @param response is a {@link com.zextras.carbonio.preview.queries.BlobResponse} object to
   * convert.
   * @param nodeId is a {@link String} representing the node id.
   *
   * @return generated {@link Query}
   */
  private Try<BlobResponse> mapResponseToBlobResponse(
    Try<com.zextras.carbonio.preview.queries.BlobResponse> response,
    String nodeId
  ) {
    return (response.isSuccess())
      ? Try.success(new BlobResponse(
        response.get().getContent(),
        nodeRepository.getNode(nodeId).get().getFullName(),
        response.get().getLength(),
        response.get().getMimeType()
      )
    )
      : Try.failure(response.failed().get());
  }
}