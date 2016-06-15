/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2016 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package com.adobe.acs.commons.workflow.bulk.execution;

import com.adobe.acs.commons.util.QueryHelper;
import com.adobe.acs.commons.workflow.bulk.execution.impl.SubStatus;
import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.commons.scheduler.ScheduleOptions;

import javax.jcr.RepositoryException;

public interface BulkWorkflowRunner {
    Runnable run(Config config);

    ScheduleOptions getOptions(Config config);

    /**
     * Initialize the Bulk Workflow Manager jcr:content node and build out the batch structure.
     *
     * @param config bulk workflow manager config obj
     * @throws PersistenceException
     * @throws RepositoryException
     */
    void initialize(QueryHelper queryHelper, Config config) throws PersistenceException,
            RepositoryException;

    void initialize(Workspace workspace, int totalCount) throws PersistenceException;

    void start(Workspace workspace) throws PersistenceException;

    void stopping(Workspace workspace) throws PersistenceException;

    void stop(Workspace workspace) throws PersistenceException;

    void stop(Workspace workspace, SubStatus subStatus) throws PersistenceException;

    void stopWithError(Workspace workspace) throws PersistenceException;

    void complete(Workspace workspace) throws PersistenceException;

    void run(Workspace workspace, Payload payload);

    void complete(Workspace workspace, Payload payload) throws Exception;

    void forceTerminate(Workspace workspace, Payload payload) throws Exception;
}

