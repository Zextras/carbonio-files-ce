// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.files.config;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.zextras.carbonio.files.cache.CacheHandlerFactory;
import com.zextras.carbonio.files.dal.repositories.impl.ebean.FileVersionRepositoryEbean;
import com.zextras.carbonio.files.dal.repositories.impl.ebean.LinkRepositoryEbean;
import com.zextras.carbonio.files.dal.repositories.impl.ebean.NodeRepositoryEbean;
import com.zextras.carbonio.files.dal.repositories.impl.ebean.ShareRepositoryEbean;
import com.zextras.carbonio.files.dal.repositories.impl.ebean.TombstoneRepositoryEbean;
import com.zextras.carbonio.files.dal.repositories.impl.ebean.UserRepositoryRest;
import com.zextras.carbonio.files.dal.repositories.interfaces.FileVersionRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.LinkRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.NodeRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.ShareRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.TombstoneRepository;
import com.zextras.carbonio.files.dal.repositories.interfaces.UserRepository;
import com.zextras.carbonio.files.graphql.validators.GenericControllerEvaluatorFactory;

public class FilesModule extends AbstractModule {

  @Override
  public void configure() {
    bind(NodeRepository.class).to(NodeRepositoryEbean.class);
    bind(ShareRepository.class).to(ShareRepositoryEbean.class);
    bind(TombstoneRepository.class).to(TombstoneRepositoryEbean.class);
    bind(FileVersionRepository.class).to(FileVersionRepositoryEbean.class);
    bind(LinkRepository.class).to(LinkRepositoryEbean.class);
    bind(UserRepository.class).to(UserRepositoryRest.class);

    install(new FactoryModuleBuilder().build(CacheHandlerFactory.class));

    install(new FactoryModuleBuilder().build(GenericControllerEvaluatorFactory.class));
  }
}
