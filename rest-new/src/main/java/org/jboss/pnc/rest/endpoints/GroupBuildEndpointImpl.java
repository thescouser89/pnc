/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.rest.endpoints;

import java.lang.invoke.MethodHandles;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.jboss.pnc.rest.api.parameters.BuildsFilterParameters;
import org.jboss.pnc.rest.api.parameters.PageParameters;

import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.GroupBuild;
import org.jboss.pnc.dto.GroupBuildRef;
import org.jboss.pnc.dto.requests.GroupBuildPushRequest;
import org.jboss.pnc.dto.response.Graph;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.facade.BrewPusher;
import org.jboss.pnc.facade.providers.api.BuildConfigurationProvider;
import org.jboss.pnc.facade.providers.api.BuildProvider;
import org.jboss.pnc.facade.providers.api.GroupBuildProvider;
import org.jboss.pnc.rest.api.endpoints.GroupBuildEndpoint;
import static org.jboss.pnc.rest.endpoints.BuildEndpointImpl.toBuildPageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.NotFoundException;

/**
 *
 * @author Honza Brázdil &lt;jbrazdil@redhat.com&gt;
 */
@ApplicationScoped
public class GroupBuildEndpointImpl implements GroupBuildEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Inject
    private GroupBuildProvider provider;

    @Inject
    private BuildConfigurationProvider buildConfigurationProvider;

    @Inject
    private BuildProvider buildProvider;

    @Inject
    private BrewPusher brewPusher;

    private EndpointHelper<GroupBuild, GroupBuildRef> endpointHelper;

    @PostConstruct
    public void init() {
        endpointHelper = new EndpointHelper<>(GroupBuild.class, provider);
    }

    @Override
    public Page<GroupBuild> getAll(PageParameters pageParameters) {
        return endpointHelper.getAll(pageParameters);
    }

    @Override
    public GroupBuild getSpecific(int id) {
        return endpointHelper.getSpecific(id);
    }

    @Override
    public void delete(int id) {
        endpointHelper.delete(id);
    }

    @Override
    public Page<Build> getBuilds(int id, PageParameters pageParams, BuildsFilterParameters filterParams) {
        return buildProvider.getBuildsForGroupBuild(toBuildPageInfo(pageParams, filterParams), id);
    }

    @Override
    public void brewPush(int id, GroupBuildPushRequest buildConfigSetRecordPushRequest) {
        brewPusher.pushGroup(id, buildConfigSetRecordPushRequest.getTagPrefix());
    }

    @Override
    public void cancel(int id) {
        logger.debug("Received cancel request fot Group Build {}.", id);
        if (provider.getSpecific(id) == null) {
            throw new NotFoundException("Unable to find Group Build {}." + id);
        }
        provider.cancel(id);
    }

    @Override
    public Graph<Build> getDependencyGraph(int id) {
        return buildProvider.getGroupBuildGraph(id);
    }

}
