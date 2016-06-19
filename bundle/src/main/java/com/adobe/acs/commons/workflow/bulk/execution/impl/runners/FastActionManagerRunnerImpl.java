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
import com.adobe.acs.commons.functions.BiConsumer;
import com.adobe.acs.commons.functions.Consumer;
import com.adobe.acs.commons.quickly.results.Action;
import com.adobe.acs.commons.util.QueryHelper;
import com.adobe.acs.commons.workflow.bulk.execution.BulkWorkflowRunner;
import com.adobe.acs.commons.workflow.bulk.execution.impl.Status;
import com.adobe.acs.commons.workflow.bulk.execution.impl.SubStatus;
import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Failure;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import com.adobe.acs.commons.workflow.synthetic.SyntheticWorkflowModel;
import com.adobe.acs.commons.workflow.synthetic.SyntheticWorkflowRunner;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
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
import javax.jcr.Session;
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

    @Override
    public Runnable run(Config config) {
        log.info("Bulk Workflow Manager with Fast Action Manager queues up work and begins execution in initialize()");
        return null;
    }

    @Override
    public ScheduleOptions getOptions(Config config) {
        return null;
    }

    @Override
    public void initialize(QueryHelper queryHelper, Config config) throws PersistenceException, RepositoryException {
        try {
            final Workspace workspace = config.getWorkspace();
            final ResourceResolver resourceResolver = config.getResourceResolver();
            final String actionManagerName = "Bulk Workflow Manager @ " + config.getPath();

            if (actionManagerFactory.hasActionManager(actionManagerName)) {
                workspace.setError("An Action Manager already exists with the name: " + actionManagerName);
                return;
            }

            final List<Resource> resources = queryHelper.findResources(resourceResolver,
                    config.getQueryType(),
                    config.getQueryStatement(),
                    config.getRelativePath());

            // Reset FAM tracking
            actionManagerFactory.purgeCompletedTasks();

            final ActionManager manager = actionManagerFactory.createTaskManager(
                    "Bulk Workflow Manager @ " + config.getPath(),
                    resourceResolver,
                    config.getBatchSize());

            final SyntheticWorkflowModel model = swr.getSyntheticWorkflowModel(
                    resourceResolver,
                    config.getWorkflowModelId(),
                    true);

            workspace.setActionManagerName(manager.getName());
            workspace.setTotalCount(resources.size());
            workspace.commit();

            final String workspacePath = workspace.getPath();
            final int total = resources.size();
            final int retryCount = config.getRetryCount();
            final int retryPause = config.getInterval();

            final AtomicInteger processed = new AtomicInteger(0);
            final AtomicInteger success = new AtomicInteger(0);

            for (final Resource resource : resources) {
                final String path = resource.getPath();

                // Within `withResolver` re-obtain JCR state using the provided RR
                manager.deferredWithResolver(new Consumer<ResourceResolver>() {
                    @Override
                    public void accept(ResourceResolver r) throws Exception {
                        manager.setCurrentItem(path);

                        if (retryCount > 0) {
                            try {
                                actions.retryAll(retryCount, retryPause, actions.startSyntheticWorkflows(model)).accept(r, path);
                                success.incrementAndGet();
                            } catch (Exception e) {
                                log.error("Error when processing payload [ {} ] with retries.", path, e);
                            }
                        } else {
                            try {
                                log.error(">>>ACIONS:  {}", actions);
                                actions.startSyntheticWorkflows(model).accept(r, path);
                                success.incrementAndGet();
                            } catch (Exception e) {
                                log.error("Error when processing payload [ {} ].", path, e);
                            }
                        }

                        if (total == processed.incrementAndGet()) {
                            log.error(">>> COMPLETED!");
                            complete(workspacePath, manager, success.get());
                        }
                    }
                });
            }

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

    public void complete(String workspacePath, ActionManager manager, final int success) throws PersistenceException, RepositoryException {
        ResourceResolver resourceResolver = null;

        try {
            resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
            Workspace workspace = resourceResolver.getResource(workspacePath).adaptTo(Workspace.class);

            workspace.setCompleteCount(success);
            for (com.adobe.acs.commons.fam.Failure f : manager.getFailureList()) {
                workspace.addFailure(f.getNodePath(), null, f.getTime());
            }
            super.complete(workspace);
            manager.addCleanupTask();
        } catch (LoginException e) {
            log.error("Could not obtain a fresh resource resolver to complete", e);
            e.printStackTrace();
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
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