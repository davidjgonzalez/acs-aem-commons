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
import com.adobe.acs.commons.workflow.synthetic.SyntheticWorkflowModel;
import com.adobe.acs.commons.workflow.synthetic.SyntheticWorkflowRunner;
import com.day.cq.workflow.WorkflowException;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.List;

@Component
@Service
public class SyntheticWorkflowRunnerImpl extends AbstractWorkflowRunner implements BulkWorkflowRunner {
    private static final Logger log = LoggerFactory.getLogger(SyntheticWorkflowRunnerImpl.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private Scheduler scheduler;

    @Reference
    private SyntheticWorkflowRunner swr;

    public final Runnable run(final Config jobConfig) {
        final Runnable job = new Runnable() {
            private String configPath = jobConfig.getPath();
            private Workspace workspace = jobConfig.getWorkspace();

            public void run() {
                ResourceResolver adminResourceResolver = null;
                Resource configResource = null;
                long start = System.currentTimeMillis();

                try {
                    adminResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
                    configResource = adminResourceResolver.getResource(configPath);
                    Config config = configResource.adaptTo(Config.class);
                    Workspace workspace = config.getWorkspace();

                    if (workspace.isStopped()) {
                        return;
                    }

                    adminResourceResolver.adaptTo(Session.class).getWorkspace().getObservationManager().setUserData("event-user-data:changedByWorkflowProcess");

                    boolean ignoreNonProcessSteps = true;
                    SyntheticWorkflowModel model;

                    try {
                        // Get the model once
                        model = swr.getSyntheticWorkflowModel(
                                adminResourceResolver,
                                config.getWorkflowModelId(),
                                ignoreNonProcessSteps);

                        boolean saveAfterEachWFProcess = false;
                        boolean saveAtEndOfAllWFProcesses = false;

                        int total = 0;
                        List<Payload> payloads = onboardCurrentPayloadGroup(workspace);

                        do {
                            // Saftey check; if payloads comes in null then immediately break from loop as there is no work to do
                            if(payloads == null || workspace.isStopped()) {
                                log.info("Bulk Synthetic Workflow run has been stopped.");
                                break;
                            }

                            for (Payload payload : payloads) {
                                log.info("Processing payload [ {} ~> {} ]", payload.getPath(), payload.getPayloadPath());

                                try {
                                    swr.execute(adminResourceResolver,
                                            payload.getPayloadPath(),
                                            model,
                                            saveAfterEachWFProcess,
                                            saveAtEndOfAllWFProcesses);
                                    complete(payload);
                                } catch (WorkflowException e) {
                                    fail(payload);
                                    log.warn("Synthetic Workflow could not process [ {} ]", payload.getPath(), e);
                                } catch (Exception e) {
                                    // Complete call failed; consider it failed
                                    log.warn("Complete call on [ {} ] failed", payload.getPath(), e);
                                    fail(payload);
                                }

                                total++;
                            }

                            // Save the Payload Group batch
                            long batchStart = System.currentTimeMillis();
                            adminResourceResolver.commit();

                            if (log.isDebugEnabled()) {
                                log.debug("Save last batch of [ {} ] payloads in {} ms", config.getBatchSize(), System.currentTimeMillis() - batchStart);
                                log.debug("Running total of [ {} ] payloads saved in {} ms", total, System.currentTimeMillis() - start);
                            }

                            // Get next set of payloads to process
                            payloads = onboardNextPayloadGroup(workspace);

                            if (workspace.isStopped()) {
                                log.info("Bulk Synthetic Workflow run has been stopped.");
                                break;
                            } else if(payloads != null && config.getThrottle() > 0) {
                                log.debug("Sleeping bulk workflow synthetic execution for {} seconds", config.getTimeout());
                                Thread.sleep(config.getThrottle() * 1000);
                            }
                        } while (payloads != null);

                        if (!workspace.isStopped()) {
                            complete(workspace);
                        }

                        if (adminResourceResolver.hasChanges()) {
                            adminResourceResolver.commit();
                        }

                        log.info("Grand total of [ {} ] payloads saved in {} ms", total, System.currentTimeMillis() - start);
                    } catch (Exception e) {
                        log.error("Error processing Bulk Synthetic Workflow execution.", e);
                    }
                } catch (RepositoryException e) {
                    log.error("Error processing Bulk Synthetic Workflow exection.", e);
                } catch (LoginException e) {
                    log.error("Error processing Bulk Synthetic Workflow exection.", e);
                } finally {
                    if (adminResourceResolver != null) {
                        adminResourceResolver.close();
                    }
                }
            }

            private List<Payload> onboardCurrentPayloadGroup(Workspace workspace) throws PersistenceException {
                // Synthetic workflow will only have 0 or 1 active payload groups
               return onboardPayloadGroup(workspace.getActivePayloadGroups().get(0));
            }

            private List<Payload> onboardNextPayloadGroup(Workspace workspace) throws PersistenceException {
                // Synthetic workflow will only have 0 or 1 active payload groups
                PayloadGroup payloadGroup = workspace.getActivePayloadGroups().get(0);
                workspace.removeActivePayloadGroup(payloadGroup);

                PayloadGroup nextPayloadGroup = payloadGroup.getNextPayloadGroup();
                return onboardPayloadGroup(nextPayloadGroup);
            }

            private List<Payload> onboardPayloadGroup(PayloadGroup payloadGroup) throws PersistenceException {
                if(payloadGroup == null) {
                    // payloadGroup is the last group, so return null to signify nothing left to process
                    return null;
                }

                List<Payload> payloads = payloadGroup.getPayloads();

                if (payloads.size() > 0) {
                    workspace.addActivePayloadGroup(payloadGroup);
                    for (Payload payload : payloads) {
                        payload.setStatus(Status.RUNNING);
                        workspace.addActivePayload(payload);
                    }
                }

                // Commit here so the status pulling can see what is being processed
                workspace.commit();

                return payloads;
            }

        };

        return job;
    }


    @Override
    public ScheduleOptions getOptions(Config config) {
        ScheduleOptions options = scheduler.NOW();
        options.canRunConcurrently(false);
        options.onLeaderOnly(true);
        options.name(config.getWorkspace().getJobName());

        return options;
    }

    @Override
    public void forceTerminate(Payload payload) throws PersistenceException {
        final Workspace workspace = payload.getPayloadGroup().getWorkspace();
        workspace.setStatus(Status.FORCE_TERMINATED);
    }

    @Override
    public void complete(Payload payload) throws Exception {
        // Remove active payload
        super.complete(payload);
        payload.setStatus(Status.COMPLETED);
    }
}

