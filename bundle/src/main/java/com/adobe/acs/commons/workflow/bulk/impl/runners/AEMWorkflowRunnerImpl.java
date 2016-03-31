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

package com.adobe.acs.commons.workflow.bulk.impl.runners;

import com.adobe.acs.commons.workflow.bulk.BulkWorkflowRunner;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowService;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
import com.day.cq.workflow.model.WorkflowModel;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_BATCH_TIMEOUT;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_BATCH_TIMEOUT_COUNT;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_COMPLETED_AT;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_CURRENT_BATCH;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_FORCE_TERMINATED_COUNT;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_INTERVAL;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_JOB_NAME;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_PATH;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_STARTED_AT;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_STATE;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_STOPPED_AT;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_WORKFLOW_ID;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_WORKFLOW_MODEL;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.STATE_COMPLETE;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.STATE_FORCE_TERMINATED;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.STATE_RUNNING;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.STATE_STOPPED_ERROR;

@Component
@Service
public class AEMWorkflowRunnerImpl extends AbstractWorkflowRunner implements BulkWorkflowRunner{
    private static final Logger log = LoggerFactory.getLogger(AEMWorkflowRunnerImpl.class);

    private ConcurrentHashMap<String, String> jobs = null;

    @Reference
    private WorkflowService workflowService;

    /**
     * {@inheritDoc}
     */
    @Override
    public final void start(final Resource resource) {
        final ModifiableValueMap properties = resource.adaptTo(ModifiableValueMap.class);

        final String jobName = properties.get(KEY_JOB_NAME, String.class);
        final String workflowModel = properties.get(KEY_WORKFLOW_MODEL, String.class);
        final String resourcePath = resource.getPath();
        long interval = properties.get(KEY_INTERVAL, DEFAULT_INTERVAL);

        final Runnable job = new Runnable() {

            private Map<String, String> activeWorkflows = new LinkedHashMap<String, String>();

            public void run() {
                ResourceResolver adminResourceResolver = null;
                Resource contentResource = null;

                try {
                    adminResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
                    activeWorkflows = getActiveWorkflows(adminResourceResolver, activeWorkflows);
                    contentResource = adminResourceResolver.getResource(resourcePath);

                    if (contentResource == null) {
                        log.warn("Bulk workflow process resource [ {} ] could not be found. Removing periodic job.",
                                resourcePath);
                        scheduler.removeJob(jobName);
                    } else if (activeWorkflows.isEmpty()) {
                        // Either the beginning or the end of a batch process
                        activeWorkflows = process(contentResource, workflowModel);
                    } else {
                        log.debug("Workflows for batch [ {} ] are still active.",
                                contentResource.adaptTo(ValueMap.class).get(KEY_CURRENT_BATCH, "Missing batch"));

                        final ValueMap properties = contentResource.adaptTo(ValueMap.class);
                        final int batchTimeout = properties.get(KEY_BATCH_TIMEOUT, 0);

                        final Resource currentBatch = getCurrentBatch(contentResource);
                        final ModifiableValueMap currentBatchProperties =
                                currentBatch.adaptTo(ModifiableValueMap.class);

                        final int batchTimeoutCount = currentBatchProperties.get(KEY_BATCH_TIMEOUT_COUNT, 0);

                        if (batchTimeoutCount >= batchTimeout) {
                            terminateActiveWorkflows(adminResourceResolver,
                                    contentResource,
                                    activeWorkflows);
                            // Next batch will be pulled on next iteration
                        } else {
                            // Still withing batch processing range; continue.
                            currentBatchProperties.put(KEY_BATCH_TIMEOUT_COUNT, batchTimeoutCount + 1);
                            adminResourceResolver.commit();
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing periodic execution: {}", e);

                    try {
                        if (contentResource != null) {
                            stop(contentResource, STATE_STOPPED_ERROR);
                        } else {
                            scheduler.removeJob(jobName);
                            log.error("Removed scheduled job [ {} ] due to errors content resource [ {} ] could not "
                                    + "be found.", jobName, resourcePath);
                        }
                    } catch (Exception ex) {
                        scheduler.removeJob(jobName);
                        log.error("Removed scheduled job [ {} ] due to errors and could not stop normally.", jobName);
                    }
                } finally {
                    if (adminResourceResolver != null) {
                        adminResourceResolver.close();
                    }
                }
            }
        };

        try {
            final boolean canRunConcurrently = false;
            scheduler.addPeriodicJob(jobName, job, null, interval, canRunConcurrently);
            jobs.put(resource.getPath(), jobName);

            log.debug("Added tracking for job [ {} , {} ]", resource.getPath(), jobName);
            log.info("Periodic job added for [ {} ] every [ {} seconds ]", jobName, interval);

            properties.put(KEY_STATE, STATE_RUNNING);
            properties.put(KEY_STARTED_AT, Calendar.getInstance());

            resource.getResourceResolver().commit();

        } catch (Exception e) {
            log.error("Error starting bulk workflow management. {}", e);
        }

        log.info("Completed starting of Bulk Workflow Manager");
    }

    @Override
    protected Map<String, String> process(Resource resource, String workflowModel) throws WorkflowException, PersistenceException, RepositoryException {
        return null;
    }


    /**
     * Retrieves the active workflows for the batch.
     *
     * @param resourceResolver the resource resolver
     * @param workflowMap      the map tracking what batch items are under WF
     * @return the updated map of which batch items and their workflow state
     * @throws RepositoryException
     * @throws PersistenceException
     */
    private Map<String, String> getActiveWorkflows(ResourceResolver resourceResolver,
                                                   final Map<String, String> workflowMap)
            throws RepositoryException, PersistenceException {

        final Map<String, String> activeWorkflowMap = new LinkedHashMap<String, String>();
        final WorkflowSession workflowSession =
                workflowService.getWorkflowSession(resourceResolver.adaptTo(Session.class));

        boolean dirty = false;
        for (final Map.Entry<String, String> entry : workflowMap.entrySet()) {
            final String workflowId = entry.getValue();

            final Workflow workflow;
            try {
                workflow = workflowSession.getWorkflow(workflowId);
                if (workflow.isActive()) {
                    activeWorkflowMap.put(entry.getKey(), workflow.getId());
                }

                final Resource resource = resourceResolver.getResource(entry.getKey());
                final ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);

                if (!StringUtils.equals(mvm.get(KEY_STATE, String.class), workflow.getState())) {
                    mvm.put(KEY_STATE, workflow.getState());
                    dirty = true;
                }
            } catch (WorkflowException e) {
                log.error("Could not get workflow with id [ {} ]. {}", workflowId, e);
            }
        }

        if (dirty) {
            resourceResolver.commit();
        }

        return activeWorkflowMap;
    }


    /**
     * Stops the bulk workflow process.
     *
     * @param resource the jcr:content configuration resource
     * @throws PersistenceException
     */
    private void stop(final Resource resource, final String state) throws PersistenceException {
        final ModifiableValueMap properties = resource.adaptTo(ModifiableValueMap.class);
        final String jobName = properties.get(KEY_JOB_NAME, String.class);

        log.debug("Stopping job [ {} ]", jobName);

        if (StringUtils.isNotBlank(jobName)) {
            scheduler.removeJob(jobName);
            jobs.remove(resource.getPath());

            log.info("Bulk Workflow Manager stopped for [ {} ]", jobName);

            properties.put(KEY_STATE, state);
            properties.put(KEY_STOPPED_AT, Calendar.getInstance());

            resource.getResourceResolver().commit();
        } else {
            log.error("Trying to stop a job without a name from Bulk Workflow Manager resource [ {} ]",
                    resource.getPath());
        }
    }




    /**
     * Updates the bulk workflow process status to be completed.
     *
     * @param resource the jcr:content configuration resource
     * @throws PersistenceException
     */
    private void complete(final Resource resource) throws PersistenceException {
        final ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);
        final String jobName = mvm.get(KEY_JOB_NAME, String.class);

        if (StringUtils.isNotBlank(jobName)) {
            scheduler.removeJob(jobName);

            log.info("Bulk Workflow Manager completed for [ {} ]", jobName);

            mvm.put(KEY_STATE, STATE_COMPLETE);
            mvm.put(KEY_COMPLETED_AT, Calendar.getInstance());

            resource.getResourceResolver().commit();
        } else {
            log.error("Trying to complete a job without a name from Bulk Workflow Manager resource [ {} ]",
                    resource.getPath());
        }
    }




    /**
     * Deletes the Workflow instances for each item in the batch.
     *
     * @param batchResource the batch resource
     * @return the number of workflow instances purged
     * @throws RepositoryException
     */
    private int purge(Resource batchResource) throws RepositoryException {
        final ResourceResolver resourceResolver = batchResource.getResourceResolver();
        final List<String> payloadPaths = new ArrayList<String>();

        for (final Resource child : batchResource.getChildren()) {
            final ModifiableValueMap properties = child.adaptTo(ModifiableValueMap.class);
            final String workflowId = properties.get(KEY_WORKFLOW_ID, "Missing WorkflowId");
            final String path = properties.get(KEY_PATH, "Missing Path");

            final Resource resource = resourceResolver.getResource(workflowId);
            if (resource != null) {
                final Node node = resource.adaptTo(Node.class);
                node.remove();
                payloadPaths.add(path);
            } else {
                log.warn("Could not find workflowId at [ {} ] to purge.", workflowId);
            }
        }

        if (payloadPaths.size() > 0) {
            resourceResolver.adaptTo(Session.class).save();
            log.info("Purged {} workflow instances for payloads: {}",
                    payloadPaths.size(),
                    Arrays.toString(payloadPaths.toArray(new String[payloadPaths.size()])));
        }

        return payloadPaths.size();
    }



    /**
     * Terminate active workflows.
     *
     * @param resourceResolver
     * @param contentResource
     * @param workflowMap
     * @return number of terminated workflows
     * @throws RepositoryException
     * @throws PersistenceException
     */
    private int terminateActiveWorkflows(ResourceResolver resourceResolver,
                                         final Resource contentResource,
                                         final Map<String, String> workflowMap)
            throws RepositoryException, PersistenceException {

        final WorkflowSession workflowSession =
                workflowService.getWorkflowSession(resourceResolver.adaptTo(Session.class));

        boolean dirty = false;
        int count = 0;
        for (final Map.Entry<String, String> entry : workflowMap.entrySet()) {
            final String workflowId = entry.getValue();

            final Workflow workflow;
            try {
                workflow = workflowSession.getWorkflow(workflowId);
                if (workflow.isActive()) {

                    workflowSession.terminateWorkflow(workflow);

                    count++;

                    log.info("Terminated workflow [ {} ]", workflowId);

                    final Resource resource = resourceResolver.getResource(entry.getKey());
                    final ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);

                    mvm.put(KEY_STATE, STATE_FORCE_TERMINATED.toUpperCase());

                    dirty = true;
                }

            } catch (WorkflowException e) {
                log.error("Could not get workflow with id [ {} ]. {}", workflowId, e);
            }
        }

        if (dirty) {
            final ModifiableValueMap properties = contentResource.adaptTo(ModifiableValueMap.class);
            properties.put(KEY_FORCE_TERMINATED_COUNT,
                    properties.get(KEY_FORCE_TERMINATED_COUNT, 0) + count);
            resourceResolver.commit();
        }

        return count;
    }


    /**
     * Processes the bulk process workflow batch; starts WF's as necessary for each batch item.
     *
     * @param resource the jcr:content configuration resource
     * @throws PersistenceException
     */
    private Map<String, String> process(final Resource resource, String workflowModel) throws
            WorkflowException, PersistenceException, RepositoryException {

        // This method can be invoked by the very first processing of a batch node, or when the batch is complete

        if (log.isDebugEnabled()) {
            log.debug("Processing batch [ {} ] with workflow model [ {} ]", this.getCurrentBatch(resource).getPath(),
                    workflowModel);
        }

        final Session session = resource.getResourceResolver().adaptTo(Session.class);
        final WorkflowSession workflowSession = workflowService.getWorkflowSession(session);
        final WorkflowModel model = workflowSession.getModel(workflowModel);

        final Map<String, String> workflowMap = new LinkedHashMap<String, String>();

        final Resource currentBatch = this.getCurrentBatch(resource);
        final ModifiableValueMap currentProperties = currentBatch.adaptTo(ModifiableValueMap.class);

        Resource batchToProcess;
        if (currentProperties.get(KEY_STARTED_AT, Date.class) == null) {
            currentProperties.put(KEY_STARTED_AT, Calendar.getInstance());
            batchToProcess = currentBatch;
            log.debug("Virgin batch [ {} ]; preparing to initiate WF.", currentBatch.getPath());
        } else {
            batchToProcess = this.advance(resource);
            log.debug("Completed batch [ {} ]; preparing to advance and initiate WF on next batch [ {} ].",
                    currentBatch.getPath(), batchToProcess);
        }

        if (batchToProcess != null) {
            for (final Resource child : batchToProcess.getChildren()) {
                final ModifiableValueMap properties = child.adaptTo(ModifiableValueMap.class);

                final String state = properties.get(KEY_STATE, "");
                final String payloadPath = properties.get(KEY_PATH, String.class);

                if (StringUtils.isBlank(state)
                        && StringUtils.isNotBlank(payloadPath)) {

                    // Don't try to restart already processed batch items

                    final Workflow workflow = workflowSession.startWorkflow(model,
                            workflowSession.newWorkflowData("JCR_PATH", payloadPath));
                    properties.put(KEY_WORKFLOW_ID, workflow.getId());
                    properties.put(KEY_STATE, workflow.getState());

                    workflowMap.put(child.getPath(), workflow.getId());
                }
            }
        } else {
            log.error("Cant find the current batch to process.");
        }

        resource.getResourceResolver().commit();

        log.debug("Bulk workflow batch tracking map: {}", workflowMap);
        return workflowMap;
    }


}
