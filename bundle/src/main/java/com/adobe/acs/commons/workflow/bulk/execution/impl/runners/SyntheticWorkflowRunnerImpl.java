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

import com.adobe.acs.commons.fam.ActionManager;
import com.adobe.acs.commons.fam.ActionManagerFactory;
import com.adobe.acs.commons.fam.DeferredActions;
import com.adobe.acs.commons.functions.BiFunction;
import com.adobe.acs.commons.functions.Consumer;
import com.adobe.acs.commons.workflow.bulk.execution.BulkWorkflowRunner;
import com.adobe.acs.commons.workflow.bulk.execution.impl.Status;
import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.PayloadGroup;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import com.adobe.acs.commons.workflow.synthetic.SyntheticWorkflowModel;
import com.adobe.acs.commons.workflow.synthetic.SyntheticWorkflowRunner;
import com.day.cq.workflow.WorkflowException;
import org.apache.commons.collections.ListUtils;
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

import javax.jcr.Session;
import java.util.List;

import static org.apache.sling.api.scripting.SlingBindings.LOG;

@Component
@Service
public class SyntheticWorkflowRunnerImpl extends AbstractWorkflowRunner implements BulkWorkflowRunner {
    private static final Logger log = LoggerFactory.getLogger(SyntheticWorkflowRunnerImpl.class);

    @Reference
    private ActionManagerFactory actionManagerFactory;

    @Reference
    private DeferredActions actions;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private Scheduler scheduler;

    @Reference
    private SyntheticWorkflowRunner swr;

    public final Runnable run(final Config jobConfig) {
        final Runnable job = new Runnable() {
            private String configPath = jobConfig.getPath();

            public void run() {
                ResourceResolver jobResourceResolver = null;
                Resource configResource = null;
                long start = System.currentTimeMillis();
                int total = 0;

                try {
                    jobResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
                    configResource = jobResourceResolver.getResource(configPath);
                    Config config = configResource.adaptTo(Config.class);
                    Workspace workspace = config.getWorkspace();

                    if (workspace.isStopped()) { return; }

                    try {
                        SyntheticWorkflowModel model = swr.getSyntheticWorkflowModel(jobResourceResolver, config.getWorkflowModelId(), true);
                        jobResourceResolver.adaptTo(Session.class).getWorkspace().getObservationManager().setUserData("changedByWorkflowProcess");

                        List<Payload> payloads = null;
                        PayloadGroup payloadGroup = null;

                        do {
                            payloads = onboardPayloadGroup(workspace);

                            // Safety check; if payloads comes in null then immediately break from loop as there is no work to do
                            if (payloads == null ) {  break; }

                            int batchCount = 0;
                            for (Payload payload : payloads) {
                                if (workspace.isStopping() || workspace.isStopped()) {
                                    stop(workspace);
                                    break;
                                }

                                try {
                                    log.info("Processing payload [ {} ]", payload.getPayloadPath());
                                    process(jobResourceResolver, model, payload);
                                    Thread.sleep(2000);
                                } catch (WorkflowException e) {
                                    fail(payload);
                                    log.warn("Synthetic Workflow could not process [ {} ]", payload.getPath(), e);
                                } catch (Exception e) {
                                    // Complete call failed; consider it failed
                                    log.warn("Complete call on [ {} ] failed", payload.getPath(), e);
                                    fail(payload);
                                }

                                batchCount++;
                                total++;
                            }

                            // Save the Payload Group batch
                            long batchStart = System.currentTimeMillis();
                            workspace.commit();

                            if (log.isDebugEnabled()) {
                                log.debug("Save last batch of [ {} ] payloads in {} ms", batchCount, System.currentTimeMillis() - batchStart);
                                log.debug("Running total of [ {} ] payloads saved in {} ms", total, System.currentTimeMillis() - start);
                            }

                            // Get next set of payloads to process
                            // This object is generally unused; however the state prepareNextPayloadGroup sets is critical to the next iteration of the loop.
                            payloadGroup = prepareNextPayloadGroup(workspace);

                            // Persist the onboarding of the next group prior to sleeping ..
                            workspace.commit();

                            if (workspace.isStopped()) {
                                log.info("Bulk Synthetic Workflow run has been stopped.");
                                break;
                            } else if (payloadGroup != null && config.getThrottle() > 0) {
                                log.debug("Sleeping bulk workflow synthetic execution for {} seconds", config.getThrottle());
                                Thread.sleep(config.getThrottle() * 1000);
                            }
                        } while (payloadGroup != null);

                        if (!workspace.isStopped()) { complete(workspace); }

                        workspace.commit();

                        log.info("Grand total of [ {} ] payloads saved in {} ms", total, System.currentTimeMillis() - start);
                    } catch (Exception e) {
                        log.error("Error processing Bulk Synthetic Workflow execution.", e);
                    }
                } catch (LoginException e) {
                    log.error("Error processing Bulk Synthetic Workflow execution.", e);
                } finally {
                    if (jobResourceResolver != null) {
                        jobResourceResolver.close();
                    }
                }
            }

            private void process(ResourceResolver resourceResolver, final SyntheticWorkflowModel model, Payload payload) throws Exception {
                ActionManager manager = actionManagerFactory.createTaskManager("Bulk Workflow Manager", resourceResolver, 10);
                final String nodePath = payload.getPayloadPath();

                manager.deferredWithResolver(new Consumer<ResourceResolver>() {
                    @Override
                    public void accept(ResourceResolver r) throws Exception {
                        currentPath.set(nodePath);

                        actions.startSyntheticWorkflows(model).accept(r, nodePath);
                    }
                });

                manager.addCleanupTask();
                complete(payload);
            }

            /**
             * Promotes the next payload group to be processed.
             * Returns null if no more payload groups left to process - this important.
             * @param workspace the bulk workflow manager workspace.
             * @return the payloads to process for the next group, or null if nothing left to process.
             * @throws PersistenceException
             */
            private PayloadGroup prepareNextPayloadGroup(Workspace workspace) throws PersistenceException {
                // Synthetic workflow will only have 1 active payload groups
                if (workspace.getActivePayloadGroups().size() > 0) {

                    // Remove the active payload group from the active payload list
                    PayloadGroup payloadGroup = workspace.getActivePayloadGroups().get(0);
                    workspace.removeActivePayloadGroup(payloadGroup);

                    // Add the active payload group from the active payload list
                    // This will allow th next call to onboardPayloadGroup(..) to process this payload group.
                    PayloadGroup nextPayloadGroup = payloadGroup.getNextPayloadGroup();
                    workspace.addActivePayloadGroup(nextPayloadGroup);

                    return nextPayloadGroup;
                } else {
                    // This is the empty payload to process case
                    return null;
                }
            }

            private List<Payload> onboardPayloadGroup(Workspace workspace) throws PersistenceException {
                // Synthetic workflow will only have 0 or 1 active payload groups
                if (workspace.getActivePayloads().size() > 0) {
                    return workspace.getActivePayloads();
                } else if (workspace.getActivePayloadGroups().size() > 0) {
                    return onboardPayloadGroup(workspace.getActivePayloadGroups().get(0));
                } else {
                    log.debug("Could not find active payload groups to onboard");
                    return ListUtils.EMPTY_LIST;
                }
            }

            private List<Payload> onboardPayloadGroup(PayloadGroup payloadGroup) throws PersistenceException {
                if(payloadGroup == null) {
                    // payloadGroup is the last group, so return null to signify nothing left to process
                    return null;
                }

                Workspace workspace = payloadGroup.getWorkspace();
                List<Payload> payloads = payloadGroup.getPayloads();

                if (payloads.size() > 0) {
                    workspace.addActivePayloadGroup(payloadGroup);

                    for (Payload payload : payloads) {
                        if (Status.NOT_STARTED.equals(payload.getStatus())) {
                            workspace.addActivePayload(payload);
                        }
                    }
                }

                // Commit here so the status polling can see what is being processed
                workspace.commit();

                if (payloads != null && payloads.size() > 0) {
                    return payloads;
                } else {
                    return null;
                }
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

    @Override
    public void running(Payload payload) {
        super.running(payload);
    }
}

