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

import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.ScheduleOptions;

public interface BulkWorkflowRunner {
    Runnable run(Config config);

    ScheduleOptions getOptions(Config config);

    void initialize(Workspace workspace, int totalCount);

    void start(Workspace workspace) throws PersistenceException;

    void stopping(Workspace workspace) throws PersistenceException;

    void stop(Workspace workspace) throws PersistenceException;

    void stopWithError(Workspace workspace) throws PersistenceException;

    void complete(Workspace workspace) throws PersistenceException;

    void running(Payload payload);

    void complete(Payload payload) throws Exception;

    void forceTerminate(Payload payload) throws Exception;
}

