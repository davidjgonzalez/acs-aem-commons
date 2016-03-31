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

package com.adobe.acs.commons.workflow.bulk.impl;

import com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine;
import com.adobe.acs.commons.workflow.bulk.BulkWorkflowRunner;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
import com.day.cq.workflow.model.WorkflowModel;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.commons.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component(
        label = "ACS AEM Commons - Bulk Workflow Engine",
        metatype = true,
        immediate = true
)
@Service
public class BulkWorkflowEngineImpl implements BulkWorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(BulkWorkflowEngineImpl.class);

    private static final String BULK_WORKFLOW_MANAGER_PAGE_FOLDER_PATH = "/etc/acs-commons/bulk-workflow-manager";

    private static final int SAVE_THRESHOLD = 1000;

    private static final boolean DEFAULT_AUTO_RESUME = true;
    private boolean autoResume = DEFAULT_AUTO_RESUME;
    @Property(label = "Auto-Resume",
            description = "Stopping the ACS AEM Commons bundle will stop any executing Bulk Workflow processing. "
                    + "When auto-resume is enabled, it will attempt to resume 'stopped via deactivation' bulk workflow jobs "
                    + "under " + BULK_WORKFLOW_MANAGER_PAGE_FOLDER_PATH + ". [ Default: true ] ",
            boolValue = DEFAULT_AUTO_RESUME)
    public static final String PROP_AUTO_RESUME = "auto-resume";


    @Reference
    private Scheduler scheduler;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;


    private ConcurrentHashMap<String, BulkWorkflowRunner> runners = new ConcurrentHashMap<String, BulkWorkflowRunner>();

    /**
     * {@inheritDoc}
     */
    @Override
    public final void initialize(Resource resource, final ValueMap params) throws
            PersistenceException, RepositoryException {
        final ModifiableValueMap properties = resource.adaptTo(ModifiableValueMap.class);

        log.trace("Entering initialized");

        if (properties.get(KEY_INITIALIZED, false)) {
            log.warn("Refusing to re-initialize an already initialized Bulk Workflow Manager.");
            return;
        }

        properties.putAll(params);
        properties.put(KEY_JOB_NAME, resource.getPath());

        // Query for all candidate resources

        final ResourceResolver resourceResolver = resource.getResourceResolver();
        final Session session = resourceResolver.adaptTo(Session.class);
        final QueryManager queryManager = session.getWorkspace().getQueryManager();
        final QueryResult queryResult = queryManager.createQuery(properties.get(KEY_QUERY, ""),
                Query.JCR_SQL2).execute();
        final NodeIterator nodes = queryResult.getNodes();

        long size = nodes.getSize();
        if (size < 0) {
            log.debug("Using provided estimate total size [ {} ] as actual size [ {} ] could not be retrieved.",
                    properties.get(KEY_ESTIMATED_TOTAL, DEFAULT_ESTIMATED_TOTAL), size);
            size = properties.get(KEY_ESTIMATED_TOTAL, DEFAULT_ESTIMATED_TOTAL);
        }

        final int batchSize = properties.get(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE);
        final Bucket bucket = new Bucket(batchSize, size,
                resource.getChild(NN_BATCHES).getPath(), "sling:Folder");

        final String relPath = params.get(KEY_RELATIVE_PATH, "");

        // Create the structure
        String currentBatch = null;
        int total = 0;
        Node previousBatchNode = null, batchItemNode = null;
        while (nodes.hasNext()) {
            Node payloadNode = nodes.nextNode();
            log.debug("nodes has next: {}", nodes.hasNext());

            log.trace("Processing search result [ {} ]", payloadNode.getPath());

            if (StringUtils.isNotBlank(relPath)) {
                if (payloadNode.hasNode(relPath)) {
                    payloadNode = payloadNode.getNode(relPath);
                } else {
                    log.warn("Could not find node at [ {} ]", payloadNode.getPath() + "/" + relPath);
                    continue;
                }
                // No rel path, so use the Query result node as the payload Node
            }

            total++;
            final String batchPath = bucket.getNextPath(resourceResolver);

            if (currentBatch == null) {
                // Set the currentBatch to the first batch folder
                currentBatch = batchPath;
            }

            final String batchItemPath = batchPath + "/" + total;

            batchItemNode = JcrUtil.createPath(batchItemPath, SLING_FOLDER, JcrConstants.NT_UNSTRUCTURED, session,
                    false);

            log.trace("Created batch item path at [ {} ]", batchItemPath);

            JcrUtil.setProperty(batchItemNode, KEY_PATH, payloadNode.getPath());
            log.trace("Added payload [ {} ] for batch item [ {} ]", payloadNode.getPath(), batchItemNode.getPath());

            if (total % batchSize == 0) {
                previousBatchNode = batchItemNode.getParent();
            } else if ((total % batchSize == 1) && previousBatchNode != null) {
                // Set the "next batch" property, so we know what the next batch to process is when
                // the current batch is complete
                JcrUtil.setProperty(previousBatchNode, KEY_NEXT_BATCH, batchItemNode.getParent().getPath());
            }

            if (total % SAVE_THRESHOLD == 0) {
                session.save();
            }
        } // while

        if (total > 0) {
            // Set last batch's "next batch" property to complete so we know we're done
            JcrUtil.setProperty(batchItemNode.getParent(), KEY_NEXT_BATCH, STATE_COMPLETE);

            if (total % SAVE_THRESHOLD != 0) {
                session.save();
            }

            properties.put(KEY_CURRENT_BATCH, currentBatch);
            properties.put(KEY_TOTAL, total);
            properties.put(KEY_INITIALIZED, true);
            properties.put(KEY_STATE, STATE_NOT_STARTED);
            properties.put("runner", "com.adobe.acs.commons.worflow.bulk.impl.runners.SyntheticRunner");

            resource.getResourceResolver().commit();

            log.info("Completed initialization of Bulk Workflow Manager");
        } else {
            throw new IllegalArgumentException("Query returned zero results.");
        }
    }

    /**
     *
     * @param configResource
     */
    public final void start(final Resource configResource) {
        String name = configResource.getValueMap().get("runner", String.class);
        BulkWorkflowRunner runner = runners.get(name);

        if (runner != null) {
            runner.start(configResource);
        }
    }







    /**
     * Advance to the next batch and update all properties on the current and next batch nodes accordingly.
     * <p/>
     * This method assumes the current batch has been verified as complete.
     *
     * @param resource the bulk workflow manager content resource
     * @return the next batch resource to process
     * @throws PersistenceException
     * @throws RepositoryException
     */
    private Resource advance(final Resource resource) throws PersistenceException, RepositoryException {
        // Page Resource
        final ResourceResolver resourceResolver = resource.getResourceResolver();
        final ModifiableValueMap properties = resource.adaptTo(ModifiableValueMap.class);
        final boolean autoCleanupWorkflow = properties.get(KEY_PURGE_WORKFLOW, true);

        // Current Batch
        final Resource currentBatch = this.getCurrentBatch(resource);
        final ModifiableValueMap currentProperties = currentBatch.adaptTo(ModifiableValueMap.class);

        if (autoCleanupWorkflow) {
            this.purge(currentBatch);
        }

        currentProperties.put(KEY_COMPLETED_AT, Calendar.getInstance());

        // Next Batch
        final String nextBatchPath = currentProperties.get(KEY_NEXT_BATCH, STATE_COMPLETE);

        if (StringUtils.equalsIgnoreCase(nextBatchPath, STATE_COMPLETE)) {

            // Last batch

            this.complete(resource);

            properties.put(KEY_COMPLETE_COUNT,
                    properties.get(KEY_COMPLETE_COUNT, 0) + this.getSize(currentBatch.getChildren()));

            return null;

        } else {

            // Not the last batch

            final Resource nextBatch = resourceResolver.getResource(nextBatchPath);
            final ModifiableValueMap nextProperties = nextBatch.adaptTo(ModifiableValueMap.class);

            currentProperties.put(KEY_STATE, STATE_COMPLETE);
            currentProperties.put(KEY_COMPLETED_AT, Calendar.getInstance());

            nextProperties.put(KEY_STATE, STATE_RUNNING);
            nextProperties.put(KEY_STARTED_AT, Calendar.getInstance());

            properties.put(KEY_CURRENT_BATCH, nextBatch.getPath());
            properties.put(KEY_COMPLETE_COUNT,
                    properties.get(KEY_COMPLETE_COUNT, 0) + this.getSize(currentBatch.getChildren()));

            return nextBatch;
        }
    }





    /**
     * {@inheritDoc}
     */
    @Override
    public final Resource getCurrentBatch(final Resource resource) {
        final ValueMap properties = resource.adaptTo(ValueMap.class);
        final String currentBatch = properties.get(KEY_CURRENT_BATCH, "");
        final Resource currentBatchResource = resource.getResourceResolver().getResource(currentBatch);

        if (currentBatchResource == null) {
            log.error("Current batch resource [ {} ] could not be located. Cannot process Bulk workflow.",
                    currentBatch);
        }
        return currentBatchResource;
    }





    /**
     * Gets the size of an iterable; Used for getting number of items under a batch.
     *
     * @param theIterable the iterable to count
     * @return number of items in the iterable
     */
    private int getSize(Iterable<?> theIterable) {
        int count = 0;

        final Iterator<?> iterator = theIterable.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        return count;
    }
}
