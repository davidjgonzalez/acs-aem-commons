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

import com.adobe.acs.commons.util.QueryHelper;
import com.adobe.acs.commons.workflow.bulk.execution.BulkWorkflowRunner;
import com.adobe.acs.commons.workflow.bulk.execution.impl.Status;
import com.adobe.acs.commons.workflow.bulk.execution.impl.SubStatus;
import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.PayloadGroup;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import com.day.cq.commons.jcr.JcrUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.ListIterator;

import static com.day.cq.wcm.foundation.List.log;

@Component
public abstract class AbstractWorkflowRunner implements BulkWorkflowRunner {
    private static final int SAVE_THRESHOLD = 1000;

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(QueryHelper queryHelper, Config config) throws
            PersistenceException, RepositoryException {

        // Query for all candidate resources
        final ResourceResolver resourceResolver = config.getResourceResolver();
        final List<Resource> resources = queryHelper.findResources(resourceResolver,
                config.getQueryType(),
                config.getQueryStatement(),
                config.getRelativePath());

        int total = 0;

        // Create node to store the run current working set
        Node workspace = JcrUtils.getOrAddNode(config.getResource().adaptTo(Node.class), Workspace.NN_WORKSPACE, Workspace.NT_UNORDERED);
        Node currentPayloads = JcrUtils.getOrCreateByPath(workspace, Workspace.NN_PAYLOADS, true, Workspace.NT_UNORDERED, Workspace.NT_UNORDERED, false);

        JcrUtil.setProperty(workspace, Workspace.PN_ACTIVE_PAYLOAD_GROUPS, new String[]{currentPayloads.getPath()});

        ListIterator<Resource> itr = resources.listIterator();

        boolean firstPayloadGroup = true;
        List<String> activePayloads = new ArrayList<String>();

        while (itr.hasNext()) {
            Resource payload = itr.next();

            log.debug("Initializing payload with search result [ {} ]", payload.getPath());

            if (StringUtils.isNotBlank(config.getRelativePath())) {
                if (payload.getChild(config.getRelativePath()) != null) {
                    payload = payload.getChild(config.getRelativePath());
                } else {
                    log.warn("Could not find node at [ {} ]", payload.getPath() + "/" + config.getRelativePath());
                    continue;
                }
                // No rel path, so use the Query result node as the payload Node
            }

            total++;

            Node payloadNode = JcrUtils.getOrCreateByPath(currentPayloads, Payload.NN_PAYLOAD, true, Workspace.NT_UNORDERED, Workspace.NT_UNORDERED, false);
            JcrUtil.setProperty(payloadNode, "path", payload.getPath());
            if (firstPayloadGroup) {
                activePayloads.add(payloadNode.getPath());
            }

            if (total % config.getBatchSize() == 0 && itr.hasNext()) {
                // payload group is complete; save...
                Node tmpPayloads = JcrUtils.getOrCreateByPath(workspace, Workspace.NN_PAYLOADS, true, Workspace.NT_UNORDERED, Workspace.NT_UNORDERED, false);
                JcrUtil.setProperty(currentPayloads, PayloadGroup.PN_NEXT, tmpPayloads.getPath());
                currentPayloads = tmpPayloads;

                if (firstPayloadGroup) {
                    firstPayloadGroup = false;
                    JcrUtil.setProperty(workspace, Workspace.PN_ACTIVE_PAYLOADS, activePayloads.toArray(new String[activePayloads.size()]));
                }
            }

            if (total % SAVE_THRESHOLD == 0) {
                resourceResolver.commit();
            } else if (!itr.hasNext()) {
                // All search results are processed
                resourceResolver.commit();
            }
        } // while

        if (firstPayloadGroup) {
            // if batch size is larger than results...
            JcrUtil.setProperty(workspace, Workspace.PN_ACTIVE_PAYLOADS, activePayloads.toArray(new String[activePayloads.size()]));
            resourceResolver.commit();
        }


        if (total > 0) {
            config.getWorkspace().getRunner().initialize(config.getWorkspace(), total);
            config.commit();

            log.info("Completed initialization of Bulk Workflow Manager");
        } else {
            throw new IllegalArgumentException("Query returned zero results.");
        }
    }


    public void initialize(Workspace workspace, int totalCount) throws PersistenceException {
        workspace.setInitialized(true);
        workspace.setTotalCount(totalCount);
        workspace.commit();
    }

    public void start(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.RUNNING);
        if (workspace.getStartedAt() == null) {
            workspace.setStartedAt(Calendar.getInstance());
        }
        workspace.commit();
    }

    public void stopping(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.RUNNING, SubStatus.STOPPING);
        workspace.commit();
    }

    public void stop(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.STOPPED);
        workspace.setStoppedAt(Calendar.getInstance());
        workspace.commit();
    }

    public void stop(Workspace workspace, SubStatus subStatus) throws PersistenceException {
        if (subStatus != null) {
            workspace.setStatus(Status.STOPPED, subStatus);
        } else {
            workspace.setStatus(Status.STOPPED);
        }
        workspace.setStoppedAt(Calendar.getInstance());
        workspace.commit();
    }

    public void stopWithError(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.STOPPED, SubStatus.ERROR);
        workspace.setStoppedAt(Calendar.getInstance());
        workspace.commit();
    }

    public void complete(Workspace workspace) throws PersistenceException {
        workspace.setStatus(Status.COMPLETED);
        workspace.setCompletedAt(Calendar.getInstance());
        workspace.commit();
    }

    public void run(Workspace workspace, Payload payload) {
        payload.setStatus(Status.RUNNING);
    }

    public void complete(Workspace workspace, Payload payload) throws Exception {
        // Remove active payload
        if (workspace != null) {
            workspace.removeActivePayload(payload);

            // Increment the complete count
            workspace.incrementCompleteCount();
        } else {
            log.warn("Unable to processing complete for payload [ {} ~> {} ]", payload.getPath(), payload.getPayloadPath());
        }
    }

    public void fail(Workspace workspace, Payload payload) throws Exception {
        // Remove active payload
        workspace.removeActivePayload(payload);

        // Increment the complete count
        workspace.incrementFailCount();

        // Track the failure details
        workspace.addFailure(payload);
    }

    public abstract void forceTerminate(Workspace workspace, Payload payload) throws Exception;
}
