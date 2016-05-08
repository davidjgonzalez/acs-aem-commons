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
import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.PayloadGroup;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowService;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
import com.day.cq.workflow.model.WorkflowModel;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Component
@Service
public class AEMWorkflowRunnerImpl extends AbstractWorkflowRunner implements BulkWorkflowRunner {
    private static final Logger log = LoggerFactory.getLogger(AEMWorkflowRunnerImpl.class);

    @Reference
    private WorkflowService workflowService;

    @Reference
    private Scheduler scheduler;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    /**
     * {@inheritDoc}
     */
    @Override
    public final Runnable run(final Config jobConfig) {
        final Runnable job = new Runnable() {

            private String configPath = jobConfig.getPath();
            private String jobName = jobConfig.getWorkspace().getJobName();

            public void run() {
                log.debug("Running AEM Bulk Workflow job [ {} ]", jobName);

                ResourceResolver adminResourceResolver = null;
                Resource configResource = null;

                try {
                    adminResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
                    configResource = adminResourceResolver.getResource(configPath);
                    Config config = null;

                    if (configResource != null) {
                        config = configResource.adaptTo(Config.class);
                    }

                    if (config == null) {
                        log.error("Bulk workflow process resource [ {} ] could not be found. Removing periodic job.",
                                configPath);
                        scheduler.unschedule(jobName);
                    } else {
                        final List<Payload> priorActivePayloads = config.getWorkspace().getActivePayloads();
                        final List<Payload> currentActivePayloads = new ArrayList<Payload>();

                        for (Payload payload : priorActivePayloads) {
                            log.debug("Checking status of payload [ {} ~> {} ]", payload.getPath(), payload.getPayloadPath());
                            Workflow workflow;
                            try {
                                workflow = payload.getWorkflow();

                                // First check if workflow is complete (aka not active)
                                if (workflow == null) {
                                    // Something bad happened; Workflow is missing.
                                    // This could be a result of a purge.
                                    // Dont know what the status is so mark as Force Terminated
                                    forceTerminate(payload);
                                } else if (!workflow.isActive()) {
                                    // Workflow has ended, so mark payload as complete
                                    payload.updateWith(workflow);
                                    complete(payload);
                                } else {
                                    // If active, check that the workflow has not expired
                                    Calendar now = Calendar.getInstance();
                                    Calendar expiresAt = Calendar.getInstance();
                                    expiresAt.setTime(workflow.getTimeStarted());
                                    expiresAt.add(Calendar.SECOND, config.getTimeout());

                                    if (!now.before(expiresAt)) {
                                        payload.updateWith(workflow);
                                        forceTerminate(payload);
                                    } else {
                                        // Finally, if active and not expired, update status and let the workflow continue
                                        payload.updateWith(workflow);
                                        currentActivePayloads.add(payload);
                                    }
                                }
                            } catch (WorkflowException e) {
                                // Logged in Payload class
                                forceTerminate(payload);
                            }
                        }

                        int capacity = config.getBatchSize() - currentActivePayloads.size();

                        log.debug("Available batch capacity is [ {} ]", capacity);

                        WorkflowSession workflowSession =
                                workflowService.getWorkflowSession(adminResourceResolver.adaptTo(Session.class));

                        WorkflowModel workflowModel = workflowSession.getModel(config.getWorkflowModelId());

                        while (capacity > 0) {
                            // Bring new payloads into the active workspace
                            Payload payload = onboardNextPayload(config.getWorkspace());
                            if (payload != null) {
                                log.debug("Onboarding payload [ {} ~> {} ]", payload.getPath(), payload.getPayloadPath());
                                Workflow workflow = workflowSession.startWorkflow(workflowModel,
                                        workflowSession.newWorkflowData("JCR_PATH", payload.getPayloadPath()));
                                payload.updateWith(workflow);
                                currentActivePayloads.add(payload);
                                capacity--;
                            } else {
                                // This means there is nothing
                               break;
                            }
                        }

                        // Check if we are in a completed state for the entire workspace.
                        if (currentActivePayloads.size() == 0) {
                            // We are done! Everything is processed and nothing left to onboard.
                            complete(config.getWorkspace());
                        }

                        config.commit();
                    }
                } catch (Exception e) {
                    log.error("Error processing periodic execution: {}", e);

                    try {
                        if (configResource != null) {
                            scheduler.unschedule(jobName);
                            stopWithError(jobConfig.getWorkspace());
                        } else {
                            scheduler.unschedule(jobName);
                            log.error("Removed scheduled job [ {} ] due to errors content resource [ {} ] could not "
                                    + "be found.", jobName, configPath);
                        }
                    } catch (Exception ex) {
                        scheduler.unschedule(jobName);
                        log.error("Removed scheduled job [ {} ] due to errors and could not stop normally.", jobName);
                    }
                } finally {
                    if (adminResourceResolver != null) {
                        adminResourceResolver.close();
                    }
                }
            }
        };

        return job;
    }

    @Override
    public ScheduleOptions getOptions(Config config) {
        ScheduleOptions options = scheduler.NOW(-1, config.getInterval());
        options.canRunConcurrently(false);
        options.onLeaderOnly(true);
        options.name(config.getWorkspace().getJobName());

        return options;
    }

    @Override
    public void complete(Payload payload) throws Exception {
        super.complete(payload);

        if (payload.getPayloadGroup().getWorkspace().getConfig().isPurgeWorkflow()) {
            try {
                purge(payload);
            } catch (WorkflowException e) {
                throw new Exception(e);
            }
        }
    }

    @Override
    public void forceTerminate(Payload payload) throws Exception {
        final Workspace workspace = payload.getPayloadGroup().getWorkspace();
        final WorkflowSession workflowSession =
                workflowService.getWorkflowSession(payload.getResourceResolver().adaptTo(Session.class));

        Workflow workflow = null;
        try {
            workflow = payload.getWorkflow();

        if (workflow != null) {
            if (workflow.isActive()) {
                workflowSession.terminateWorkflow(workflow);

                log.info("Force Terminated workflow [ {} ]", workflow.getId());

                workspace.setStatus(Status.FORCE_TERMINATED);

                if (workspace.getConfig().isPurgeWorkflow()) {
                    purge(payload);
                }
            } else {
                log.warn("Trying to force terminate an inactive workflow [ {} ]", workflow.getId());
            }
        } else {
            workspace.setStatus(Status.FORCE_TERMINATED);
        }
        } catch (WorkflowException e) {
            throw new Exception(e);
        }
    }

    private void purge(Payload payload) throws PersistenceException, WorkflowException {
        Workflow workflow = payload.getWorkflow();
        ResourceResolver resourceResolver = payload.getResourceResolver();

        if (workflow != null) {
            final Resource resource = resourceResolver.getResource(workflow.getId());

            if (resource != null) {
                try {
                    String path = resource.getPath();
                    resource.adaptTo(Node.class).remove();
                    log.info("Purging working instance [ {} ]", path);
                } catch (RepositoryException e) {
                    throw new PersistenceException("Unable to purge workflow instance node.", e);
                }
            } else {
                log.warn("Could not find workflow instance at [ {} ] to purge.", workflow.getId());
            }
        }
    }

    /**
     * Operations
     **/
    public Payload onboardNextPayload(Workspace workspace) {
        long start = System.currentTimeMillis();

        for (PayloadGroup payloadGroup : workspace.getActivePayloadGroups()) {
            Payload payload = payloadGroup.getNextPayload();

            if (payload != null && !payload.isOnboarded()) {
                // Onboard this payload as it hasnt been onboarded yet
                workspace.addActivePayload(payload);

                if (log.isDebugEnabled()) {
                    log.debug("Took {} ms to onboard next payload", System.currentTimeMillis() - start);
                }
                return payload;
            }
        }

        // No payloads in the active payload groups are eligible for onboarding

        PayloadGroup nextPayloadGroup = null;
        for (PayloadGroup payloadGroup : workspace.getActivePayloadGroups()) {
            nextPayloadGroup = onboardNextPayloadGroup(payloadGroup);

            if (nextPayloadGroup != null) {
                Payload payload = nextPayloadGroup.getNextPayload();
                if (payload == null) {
                    continue;
                    // all done! empty group
                }

                workspace.addActivePayload(payload);

                if (log.isDebugEnabled()) {
                    log.debug("Took {} ms to onboard next payload", System.currentTimeMillis() - start);
                }

                return payload;
            } else {
                log.debug("Could not find a next payload group for [ {} ]", payloadGroup.getPath());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Took {} ms to onboard next payload", System.currentTimeMillis() - start);
        }
        return null;
    }

    public PayloadGroup onboardNextPayloadGroup(PayloadGroup payloadGroup) {
        // Assumes a next group should be onboarded
        // This method is not responsible for removing items from the activePayloadGroups
        Workspace workspace = payloadGroup.getWorkspace();

        if (payloadGroup == null) {
            return null;
        }

        PayloadGroup candidatePayloadGroup = payloadGroup.getNextPayloadGroup();

        if (candidatePayloadGroup == null) {
            // payloadGroup is the last! nothing to do!
            return null;
        } else if (workspace.isActive(candidatePayloadGroup) || candidatePayloadGroup.getNextPayload() == null) {
            // Already processing the next group, use *that* group's next group
            // OR there is nothing left in that group to process...

            // recursive call..
            return onboardNextPayloadGroup(candidatePayloadGroup);
        } else {
            // Found a good payload group! has atleast 1 payload that can be onboarded
            workspace.addActivePayloadGroup(payloadGroup);
            return candidatePayloadGroup;
        }
    }
}
