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

import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.auth.AuthenticationProvider;
import org.jboss.pnc.auth.LoggedInUser;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.User;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.facade.providers.api.BuildPageInfo;
import org.jboss.pnc.facade.providers.api.BuildProvider;
import org.jboss.pnc.facade.providers.api.UserProvider;
import org.jboss.pnc.rest.api.endpoints.UserEndpoint;
import org.jboss.pnc.rest.api.parameters.BuildsFilterParameters;
import org.jboss.pnc.rest.api.parameters.PageParameters;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import java.util.Date;

@Stateless
@Slf4j
public class UserEndpointImpl implements UserEndpoint {

    @Context
    private HttpServletRequest httpServletRequest;

    @Context
    private HttpServletResponse response2;

    @Inject
    private UserProvider userProvider;

    @Inject
    private AuthenticationProvider authenticationProvider;

    @Inject
    private BuildProvider buildProvider;

    @Override
    public User getCurrentUser() {
        LoggedInUser loginInUser = authenticationProvider.getLoggedInUser(httpServletRequest);

        if (response2 == null) {
            log.warn("response 2 is null");
        } else {
            response2.setStatus(200 + (new Date()).getSeconds());
        }
        return userProvider.getOrCreateNewUser(loginInUser.getUserName());
    }

    @Override
    public Page<Build> getBuilds(int id, PageParameters page, BuildsFilterParameters filter) {
        BuildPageInfo pageInfo = BuildEndpointImpl.toBuildPageInfo(page, filter);
        return buildProvider.getBuildsForUser(pageInfo, id);
    }
}
