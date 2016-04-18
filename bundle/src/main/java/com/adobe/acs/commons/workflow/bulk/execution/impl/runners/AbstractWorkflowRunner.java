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

package com.adobe.acs.commons.workflow.bulk.execution.impl.runners;

import com.adobe.acs.commons.workflow.bulk.execution.BulkWorkflowRunner;
import com.adobe.acs.commons.workflow.bulk.execution.impl.Status;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import org.apache.sling.api.resource.PersistenceException;

import java.util.Calendar;

public abstract class AbstractWorkflowRunner implements BulkWorkflowRunner {

    public void initialize(Workspace workspace, int totalCount) {
        workspace.setInitialized(true);
        workspace.setTotalCount(totalCount);
    }

    public void start(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.RUNNING);
        workspace.setStartedAt(Calendar.getInstance());
        workspace.commit();
    }

    public void stop(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.STOPPED);
        workspace.setStoppedAt(Calendar.getInstance());
        workspace.commit();
    }

    public void stopWithError(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.STOPPED_ERROR);
        workspace.setStoppedAt(Calendar.getInstance());
        workspace.commit();
    }

    public void complete(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.COMPLETED);
        workspace.setCompletedAt(Calendar.getInstance());
        workspace.commit();
    }

    public void complete(Payload payload) throws Exception {
        // Remove active payload
        Workspace workspace = payload.getPayloadGroup().getWorkspace();
        workspace.removeActivePayload(payload);

        // Increment the complete count
        workspace.incrementComplete();
    }

    public void fail(Payload payload) throws Exception {
        // Remove active payload
        Workspace workspace = payload.getPayloadGroup().getWorkspace();
        workspace.removeActivePayload(payload);

        // Increment the complete count
        workspace.incrementFailure();
    }

    public abstract void forceTerminate(Payload payload) throws Exception;
}
