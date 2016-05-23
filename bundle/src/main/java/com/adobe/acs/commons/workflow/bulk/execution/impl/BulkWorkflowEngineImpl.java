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

package com.adobe.acs.commons.workflow.bulk.execution.impl;

import com.adobe.acs.commons.util.QueryHelper;
import com.adobe.acs.commons.workflow.bulk.execution.BulkWorkflowEngine;
import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import com.day.cq.commons.jcr.JcrUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

@Component(
        label = "ACS AEM Commons - Bulk Workflow Engine",
        metatype = true,
        immediate = true
)
@Service
public class BulkWorkflowEngineImpl implements BulkWorkflowEngine {

    private static final String BULK_WORKFLOW_MANAGER_PAGE_FOLDER_PATH = "/etc/acs-commons/bulk-workflow-manager";
    private static final boolean DEFAULT_AUTO_RESUME = true;

    @Property(label = "Auto-Resume",
            description = "Stopping the ACS AEM Commons bundle will stop any executing Bulk Workflow processing. "
                    + "When auto-resume is enabled, it will attempt to resume 'stopped via deactivation' bulk workflow jobs "
                    + "under " + BULK_WORKFLOW_MANAGER_PAGE_FOLDER_PATH + ". [ Default: true ] ",
            boolValue = DEFAULT_AUTO_RESUME)
    public static final String PROP_AUTO_RESUME = "auto-resume";
    private static final Logger log = LoggerFactory.getLogger(BulkWorkflowEngineImpl.class);
    private static final int SAVE_THRESHOLD = 1000;

    @Reference
    private Scheduler scheduler;

    @Reference
    private QueryHelper queryHelper;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private boolean autoResume = DEFAULT_AUTO_RESUME;

    /**
     * {@inheritDoc}
     */
    @Override
    public final void initialize(Config config) throws
            PersistenceException, RepositoryException {

        if (config.getWorkspace().isInitialized()) {
            log.warn("Refusing to re-initialize an already initialized Bulk Workflow Manager.");
            return;
        }

        // Query for all candidate resources
        final ResourceResolver resourceResolver = config.getResourceResolver();
        final List<Resource> resources = queryHelper.findResources(resourceResolver,
                config.getQueryType(),
                config.getQueryStatement(),
                config.getRelativePath());

        int total = 0;

        /**
         * cq:Page
         * cq:Page/jcr:content
         * cq:Page/jcr:content/workspace
         * cq:Page/jcr:content/workspace/payloads
         * cq:Page/jcr:content/workspace/payloads-Y
         * cq:Page/jcr:content/workspace/payloads-Z
         */

        // Create node to store the running current working set
        Node workspace = JcrUtils.getOrAddNode(config.getResource().adaptTo(Node.class), "workspace", "oak:Unstructured");
        Node currentPayloads = JcrUtils.getOrCreateByPath(workspace, "payloads", true, "oak:Unstructured", "oak:Unstructured", false);

        JcrUtil.setProperty(workspace, "activePayloadGroups", new String[]{currentPayloads.getPath()});

        ListIterator<Resource> itr = resources.listIterator();

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

            Node payloadNode = JcrUtils.getOrCreateByPath(currentPayloads, "payload", true, "oak:Unstructured", "oak:Unstructured", false);
            JcrUtil.setProperty(payloadNode, "path", payload.getPath());

            if (total % config.getBatchSize() == 0 && itr.hasNext()) {
                // payload group is complete; save...
                Node tmpPayloads = JcrUtils.getOrCreateByPath(workspace, "payloads", true, "oak:Unstructured", "oak:Unstructured", false);
                JcrUtil.setProperty(currentPayloads, "next", tmpPayloads.getPath());
                currentPayloads = tmpPayloads;
            }

            if (total % SAVE_THRESHOLD == 0) {
                resourceResolver.commit();
            } else if (!itr.hasNext()) {
                // All search results are processed
                resourceResolver.commit();
            }
        } // while

        if (total > 0) {
            config.getWorkspace().getRunner().initialize(config.getWorkspace(), total);
            config.commit();

            log.info("Completed initialization of Bulk Workflow Manager");
        } else {
            throw new IllegalArgumentException("Query returned zero results.");
        }
    }

    @Override
    public final void start(Config config) throws PersistenceException {
        Workspace workspace = config.getWorkspace();

        workspace.getRunner().start(workspace);
        Runnable job = workspace.getRunner().run(config);
        ScheduleOptions options = workspace.getRunner().getOptions(config);

        scheduler.schedule(job, options);
    }

    @Override
    public void stopping(Config config) throws PersistenceException {
        Workspace workspace = config.getWorkspace();
        workspace.getRunner().stopping(workspace);
    }

    @Override
    public void stop(Config config) throws PersistenceException {
        Workspace workspace = config.getWorkspace();

        scheduler.unschedule(config.getWorkspace().getJobName());
        workspace.getRunner().stop(workspace);
    }

    @Override
    public void resume(Config config) throws PersistenceException {
        start(config);
    }

    public void complete(Config config) throws PersistenceException {
        Workspace workspace = config.getWorkspace();

        scheduler.unschedule(config.getWorkspace().getJobName());
        workspace.getRunner().complete(workspace);
    }

    public void stopDeactivate(Config config) throws PersistenceException {
        Workspace workspace = config.getWorkspace();

        scheduler.unschedule(config.getWorkspace().getJobName());
        workspace.getRunner().stop(workspace);
    }

    @Activate
    protected final void activate(final Map<String, String> args) {
        if (!PropertiesUtil.toBoolean(args.get(PROP_AUTO_RESUME), DEFAULT_AUTO_RESUME)) {
            log.debug("Auto-resume for Bulk Workflow Manager is disabled");
            return;
        }

        log.info("Looking for any Bulk Workflow Manager pages to resume processing under: {}", BULK_WORKFLOW_MANAGER_PAGE_FOLDER_PATH);

        ResourceResolver adminResourceResolver = null;
        try {
            adminResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
            final Resource root = adminResourceResolver.getResource(BULK_WORKFLOW_MANAGER_PAGE_FOLDER_PATH);

            if (root == null) {
                return;
            }

            final ConfigResourceVisitor visitor = new ConfigResourceVisitor();
            visitor.accept(root);

            final List<Config> configs = visitor.getConfigs();
            log.debug("Found [ {} ] candidate config(s) for resuming", configs.size());

            for (Config config : configs) {
                log.debug("[ {} ~> {} ]", config.getPath(), config.getWorkspace().getStatus().name());

                if (config.getWorkspace().isResumable()) {
                    log.info("Automatically resuming bulk workflow at [ {} ]", config.getPath());
                    this.resume(config);
                }
            }

        } catch (LoginException e) {
            log.error("Could not obtain resource resolver for finding stopped Bulk Workflow jobs", e);
        } catch (PersistenceException e) {
            log.error("Could not resume bulk workflow manager configuration", e);
        } finally {
            if (adminResourceResolver != null) {
                adminResourceResolver.close();
            }
        }
    }

    @Deactivate
    protected final void deactivate(final Map<String, String> args) {
        log.debug("Looking for any Bulk Workflow Manager pages to STOPPED_DEACTIVATED processing under: {}", BULK_WORKFLOW_MANAGER_PAGE_FOLDER_PATH);

        ResourceResolver adminResourceResolver = null;
        try {
            adminResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
            final Resource root = adminResourceResolver.getResource(BULK_WORKFLOW_MANAGER_PAGE_FOLDER_PATH);

            if (root == null) {
                return;
            }

            final ConfigResourceVisitor visitor = new ConfigResourceVisitor();
            visitor.accept(root);

            final List<Config> configs = visitor.getConfigs();
            log.debug("Found [ {} ] candidate config(s) for deactivated stopping",  configs.size());

            for (Config config : configs) {
                log.debug("[ {} ~> {} ]", config.getPath(), config.getWorkspace().getStatus().name());

                if (config.getWorkspace().isRunning()) {
                    log.info("Stopping bulk workflow at [ {} ] due to ACS Commons bundle deactivation", config.getPath());
                    this.stopDeactivate(config);
                }
            }
        } catch (LoginException e) {
            log.error("Could not obtain resource resolver for finding stopped Bulk Workflow jobs", e);
        } catch (PersistenceException e) {
            log.error("Could not resume bulk workflow manager configuration", e);
        } finally {
            if (adminResourceResolver != null) {
                adminResourceResolver.close();
            }
        }
    }
}
