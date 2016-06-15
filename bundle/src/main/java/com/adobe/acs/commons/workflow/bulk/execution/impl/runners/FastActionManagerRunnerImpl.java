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
import com.adobe.acs.commons.fam.ThrottledTaskRunner;
import com.adobe.acs.commons.functions.Consumer;
import com.adobe.acs.commons.util.QueryHelper;
import com.adobe.acs.commons.workflow.bulk.execution.BulkWorkflowRunner;
import com.adobe.acs.commons.workflow.bulk.execution.impl.SubStatus;
import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Failure;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import com.adobe.acs.commons.workflow.synthetic.SyntheticWorkflowModel;
import com.adobe.acs.commons.workflow.synthetic.SyntheticWorkflowRunner;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.workflow.WorkflowException;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
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
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Service
public class FastActionManagerRunnerImpl extends AbstractWorkflowRunner implements BulkWorkflowRunner {
    private static final Logger log = LoggerFactory.getLogger(FastActionManagerRunnerImpl.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private QueryHelper queryHelper;

    @Reference
    private ThrottledTaskRunner throttledTaskRunner;

    @Reference
    private ActionManagerFactory actionManagerFactory;

    @Reference
    private SyntheticWorkflowRunner swr;

    @Reference
    private DeferredActions actions;

    @Reference
    private Scheduler scheduler;

    @Override
    public Runnable run(final Config jobConfig) {
        final Runnable job = new Runnable() {
            private String configPath = jobConfig.getPath();

            public void run() {
                // Query for all candidate resources
                ResourceResolver jobResourceResolver;
                Resource configResource;

                try {
                    jobResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
                    configResource = jobResourceResolver.getResource(configPath);

                    final Config config = configResource.adaptTo(Config.class);
                    final Workspace workspace = config.getWorkspace();

                    final List<Resource> resources;
                    resources = queryHelper.findResources(jobResourceResolver,
                            config.getQueryType(),
                            config.getQueryStatement(),
                            config.getRelativePath());

                    // Reset FAM tracking
                    actionManagerFactory.purgeCompletedTasks();

                    final ActionManager manager = actionManagerFactory.createTaskManager(
                            "Bulk Workflow Manager @ " + config.getPath(),
                            jobResourceResolver,
                            config.getInterval());
                    final SyntheticWorkflowModel model = swr.getSyntheticWorkflowModel(
                            jobResourceResolver,
                            config.getWorkflowModelId(),
                            true);

                    workspace.setTotalCount(resources.size());
                    workspace.commit();

                    final AtomicInteger completeCount = new AtomicInteger(0);
                    final AtomicInteger failCount = new AtomicInteger(0);
                    final AtomicInteger runningTotal = new AtomicInteger(0);

                    final int total = resources.size();
                    final int batchSize = config.getBatchSize();
                    final String workspacePath = workspace.getPath();

                    for (final Resource resource : resources) {
                        final String path = resource.getPath();

                        // Within `withResolver` re-obtain JCR state using the provided RR
                        manager.deferredWithResolver(new Consumer<ResourceResolver>() {
                            @Override
                            public void accept(ResourceResolver r) throws Exception {
                                log.error("------------------------------------");
                                manager.setCurrentItem(path);

                                try {
                                    actions.startSyntheticWorkflows(model).accept(r, path);
                                    final int localCompleteCount = completeCount.incrementAndGet();
                                    ModifiableValueMap mvm = r.getResource(workspacePath).adaptTo(ModifiableValueMap.class);
                                    mvm.put("completeCount", localCompleteCount);
                                    if(localCompleteCount == total) {
                                        log.error(">>> JUST SET COMPLETE COUNT TO: {}", localCompleteCount);
                                    }
                                    log.error(">>> COMPLETE COUNT: {}", localCompleteCount);
                                } catch (Exception e) {
                                    failCount.incrementAndGet();
                                    log.error(">>> FAIL COUNT: {}", failCount.get());

                                    try {
                                        fail(r.getResource(workspacePath).adaptTo(Workspace.class), path);
                                    } catch (Exception e1) {
                                        log.error(">>>  COULD NOT FAIL PAYLOAD", e1);
                                        log.error(">>> Could not record failure of payload [ {} ]", path, e1);
                                    }
                                }


                                if (runningTotal.incrementAndGet() == total) {
                                    log.error(">>> RUNNING TOTAL IS EQUAL TO TOTAL. {} == {}", runningTotal.get(), total);
                                    complete(workspace);
                                } else {
                                    log.error(">>> RUNNING TOTAL COUNT: {}", runningTotal.get());
                                }
                            }
                        });
                    }

                    workspace.commit();

                    manager.addCleanupTask();
                } catch (LoginException e) {
                    log.error("Could not obtain resource resolver", e);
                } catch (WorkflowException e) {
                    log.error("Could not find a Synthetic Workflow Model", e);
                } catch (RepositoryException e) {
                    log.error("Repository exception occurred when processing FAM-based bulk workflow", e);
                } catch (PersistenceException e) {
                    log.error("Persistence exception occurred when processing FAM-based bulk workflow", e);
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
    public void initialize(QueryHelper queryHelper, Config config) throws PersistenceException, RepositoryException {
        Workspace workspace = config.getWorkspace();
        initialize(workspace, 0);
        workspace.commit();
    }

    @Override
    public void start(Workspace workspace) throws PersistenceException {
        if (!throttledTaskRunner.isRunning()) {
            throttledTaskRunner.resumeExecution();
        }
        super.start(workspace);
    }

    @Override
    public void stopping(Workspace workspace) throws PersistenceException {
        stop(workspace);
    }

    @Override
    public void stop(Workspace workspace) throws PersistenceException {
        throttledTaskRunner.pauseExecution();
        super.stop(workspace);
    }

    @Override
    public void stop(Workspace workspace, SubStatus subStatus) throws PersistenceException {
        throttledTaskRunner.pauseExecution();
        super.stop(workspace, subStatus);
    }

    @Override
    public void stopWithError(Workspace workspace) throws PersistenceException {
        throttledTaskRunner.pauseExecution();
        super.stopWithError(workspace);
    }

    @Override
    public void complete(Workspace workspace) throws PersistenceException {
        super.complete(workspace);
    }

    /**
     * Synchronized since multiple threads could be logging.
     *
     * @param workspace The workspace
     * @param path The path of the payload
     * @throws Exception
     */
    public synchronized void fail(Workspace workspace, String path) throws Exception {
        // Track the failure details
        workspace.incrementFailCount();

        Node failure = JcrUtils.getOrCreateByPath(workspace.getResourceResolver().getResource(workspace.getPath()).getChild("failures").adaptTo(Node.class), "failure", true, "oak:Unstructured", "oak:Unstructured", false);
        JcrUtil.setProperty(failure, Failure.PN_PAYLOAD_PATH, path);
        JcrUtil.setProperty(failure, Failure.PN_FAILED_AT, Calendar.getInstance());

        workspace.commit();
    }

    @Override
    public void run(Workspace workspace, Payload payload) {
        if (!throttledTaskRunner.isRunning()) {
            throttledTaskRunner.resumeExecution();
        }
    }

    @Override
    public void complete(Workspace workspace, Payload payload) throws Exception {
        throw new UnsupportedOperationException("FAM payloads cannot be completed as they are not tracked");
    }

    @Override
    public void forceTerminate(Workspace workspace, Payload payload) throws Exception {
        throw new UnsupportedOperationException("FAM jobs cannot be force terminated");
    }
}