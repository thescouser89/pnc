/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2022 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.spi.datastore;

import org.jboss.pnc.spi.coordinator.BuildTaskRef;

import java.util.Collection;
import java.util.List;

public interface BuildTaskRepository {

    // TODO might not be required, used only while processing task completion
//    Optional<BuildTask> getTask(String id);

    List<BuildTaskRef> getBuildTasksByBCSRId(Integer buildConfigSetRecordId);

    /**
     * @deprecated Used for tests only
     */
    @Deprecated // used in tests only
    Collection<BuildTaskRef> getAll();

    Collection<BuildTaskRef> getUnfinishedTasks();

    /**
     * @deprecated Used for tests only
     */
    @Deprecated
    boolean isEmpty();

    /**
     * @deprecated if needed debug info should be provided by Rex
     */
    @Deprecated
    String getDebugInfo();

}
