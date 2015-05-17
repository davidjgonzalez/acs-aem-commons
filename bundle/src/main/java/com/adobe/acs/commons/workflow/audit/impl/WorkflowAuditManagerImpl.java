package com.adobe.acs.commons.workflow.audit.impl;

import com.adobe.acs.commons.workflow.audit.WorkflowAuditManager;
import com.adobe.acs.commons.workflow.audit.WorkflowAuditUtil;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.HistoryItem;
import com.day.cq.workflow.exec.Workflow;
import com.day.jcr.vault.util.Text;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
@Service
public class WorkflowAuditManagerImpl implements WorkflowAuditManager {
    private static final Logger log = LoggerFactory.getLogger(WorkflowAuditManagerImpl.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public List<String> audit(final Workflow workflow, final WorkflowSession workflowSession) throws WorkflowException,
            RepositoryException, PersistenceException, LoginException {

        final List<String> harvestedWorkflowIds = new ArrayList<String>();

        ResourceResolver resourceResolver = null;

        try {
            resourceResolver = this.getResourceResolver(workflowSession.getSession());

            final List<HistoryItem> history = workflowSession.getHistory(workflow);

            final Resource wfResource = resourceResolver.getResource(workflow.getId());
            final Resource audit = getOrCreateAuditResource(resourceResolver, workflow);
            final ModifiableValueMap auditProperties = audit.adaptTo(ModifiableValueMap.class);
            final Calendar cal = Calendar.getInstance();

            log.debug("Processing audit entry [ {} ]", audit.getPath());

            String payload = (String) workflow.getWorkflowData().getPayload();

            // Workflow Data
            put(auditProperties, "sling:resourceType", Constants.RT_WORKFLOW_INSTANCE_AUDIT);

            put(auditProperties, "payload", workflow.getWorkflowData().getPayload());
            put(auditProperties, "payloadContent", WorkflowAuditUtil.getPayloadContent(resourceResolver, payload));
            put(auditProperties, "payloadTitle", WorkflowAuditUtil.getPayloadTitle(resourceResolver, payload));
            put(auditProperties, "payloadType", workflow.getWorkflowData().getPayloadType());

            // Workflow Model
            put(auditProperties, "modelTitle", workflow.getWorkflowModel().getTitle());
            put(auditProperties, "modelVersion", workflow.getWorkflowModel().getVersion());
            put(auditProperties, "modelDescription", workflow.getWorkflowModel().getDescription());
            put(auditProperties, "initiator", workflow.getInitiator());

            // Workflow
            put(auditProperties, "state", workflow.getState());
            put(auditProperties, "workflowId", workflow.getId());
            put(auditProperties, "active", workflow.isActive());

            if (workflow.getTimeStarted() != null) {
                cal.setTime(workflow.getTimeStarted());
                put(auditProperties, "startedAt", cal);
            }

            if (workflow.getTimeEnded() != null) {
                cal.setTime(workflow.getTimeEnded());
                put(auditProperties, "endedAt", cal);
            }

            // Workflow Metadata
            this.copyProperties(workflow.getMetaDataMap(), auditProperties);

            // History
            for (final HistoryItem historyItem : history) {
                auditHistoryItem(audit, auditProperties, historyItem);
            }

            // Collect ALL containee history items recursively
            final List<Workflow> containeeWorkflows = this.getContaineeWorkflows(wfResource, workflowSession);

            for (final Workflow containeeWorkflow : containeeWorkflows) {

                if(!this.isHarvested(resourceResolver, containeeWorkflow.getId())) {
                    // Ignore fully harvested containee Workflows
                    final List<HistoryItem> containeeHistory = workflowSession.getHistory(containeeWorkflow);

                    for (final HistoryItem historyItem : containeeHistory) {
                        this.auditHistoryItem(audit, auditProperties, historyItem);
                        harvestedWorkflowIds.add(containeeWorkflow.getId());
                    }

                    if(!containeeWorkflow.isActive()) {
                        this.setHarvested(resourceResolver, containeeWorkflow.getId());
                    }
                }
            }

            harvestedWorkflowIds.add(workflow.getId());

            log.debug("Harvest workflowIds: {}", harvestedWorkflowIds);

            this.order(audit);
            this.save(resourceResolver);

        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }

        return harvestedWorkflowIds;
    }

    @Override
    public void link(final Workflow parentWorkflow,
                     final Workflow containeeWorkflow,
                     final WorkflowSession workflowSession) throws PersistenceException, LoginException, RepositoryException {

        ResourceResolver resourceResolver = null;

        try {
            resourceResolver = this.getResourceResolver(workflowSession.getSession());

            final Resource audit = this.getOrCreateAuditResource(resourceResolver, parentWorkflow);
            final ModifiableValueMap auditProperties = audit.adaptTo(ModifiableValueMap.class);

            final Set<String> containeeInstanceIds = new HashSet<String>(Arrays.asList(
                    auditProperties.get(Constants.PN_CONTAINEE_INSTANCE_IDS, new String[]{ })));

            containeeInstanceIds.add(containeeWorkflow.getId());

            auditProperties.put(Constants.PN_CONTAINEE_INSTANCE_IDS, containeeInstanceIds.toArray(new String[]{ }));

            /* Mark the Containee Worlflow as a Containee */

            final Resource containeeResource = resourceResolver.getResource(containeeWorkflow.getId());

            if (containeeResource != null) {
                final ModifiableValueMap containeeProperties = containeeResource.adaptTo(ModifiableValueMap.class);
                containeeProperties.put(Constants.PN_IS_CONTAINEE, true);
                containeeProperties.put(Constants.PN_ORIGIN_INSTANCE_ID, parentWorkflow.getId());

            } else {
                log.warn("Could not find resource for containee Workflow [ {} }", containeeWorkflow.getId());
            }

            if (resourceResolver.hasChanges()) {
                resourceResolver.commit();
            }
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
    }

    private void auditHistoryItem(final Resource audit, final ModifiableValueMap auditProperties,
                                  final HistoryItem historyItem) throws RepositoryException {
        final String itemId = String.valueOf(historyItem.getDate().getTime());
        final Calendar cal = Calendar.getInstance();

        if (audit.getChild(itemId) == null) {
            final Resource itemResource = getOrCreateResource(audit, itemId);
            final ModifiableValueMap itemProperties = itemResource.adaptTo(ModifiableValueMap.class);

            // History WorkItem
            put(itemProperties, "sling:resourceType", Constants.RT_WORKFLOW_ITEM_AUDIT);

            put(itemProperties, "assignee", historyItem.getWorkItem().getCurrentAssignee());
            put(itemProperties, "workitemId", historyItem.getWorkItem().getId());
            put(itemProperties, "stepTitle", historyItem.getWorkItem().getNode().getTitle());

            put(itemProperties, "stepDescription", historyItem.getWorkItem().getNode().getDescription());
            put(itemProperties, "stepType", historyItem.getWorkItem().getNode().getType());
            put(itemProperties, "stepId", historyItem.getWorkItem().getNode().getId());

            if (historyItem.getWorkItem().getTimeStarted() != null) {
                cal.setTime(historyItem.getWorkItem().getTimeStarted());
                put(auditProperties, "startedAt", cal);
            }

            if (historyItem.getWorkItem().getTimeEnded() != null) {
                cal.setTime(historyItem.getWorkItem().getTimeEnded());
                put(auditProperties, "endedAt", cal);
            }

            // History Item
            put(itemProperties, "comment", historyItem.getComment());
            put(itemProperties, "action", historyItem.getAction());
            put(itemProperties, "userId", historyItem.getUserId());

            if (historyItem.getDate() != null) {
                cal.setTime(historyItem.getDate());
                put(itemProperties, "date", cal);
            }

            final Resource metadataResource = getOrCreateResource(itemResource, "metadata");
            final ModifiableValueMap metadataProperties = metadataResource.adaptTo(ModifiableValueMap.class);

            this.copyProperties(historyItem.getWorkItem().getMetaDataMap(), metadataProperties);
        }
    }

    private List<Workflow> getContaineeWorkflows(final Resource wfResource,
                                                 final WorkflowSession worfkflowSession) throws WorkflowException {

        final List<Workflow> containeeWorkflows = new ArrayList<Workflow>();

        final ValueMap properties = wfResource.adaptTo(ValueMap.class);

        // Get the Containee Workflow IDs
        final String[] containeeWorkflowIds = properties.get(Constants.PN_CONTAINEE_INSTANCE_IDS, new String[]{ });

        for (final String containeeWorkflowId : containeeWorkflowIds) {
            // If this workflow does have a sub-workflow

            // Get the sub workflow
            final Workflow workflow = worfkflowSession.getWorkflow(containeeWorkflowId);

            if (workflow != null) {
                // Add the sub-workflow to the list
                containeeWorkflows.add(workflow);

                // Add any sub-workflows for the sub-workflow (recursively)
                containeeWorkflows.addAll(this.getContaineeWorkflows(wfResource.getResourceResolver().getResource
                        (containeeWorkflowId), worfkflowSession));
            } else {
                log.warn("Could not find containee workflow id [ {} ]", containeeWorkflowId);
            }
        }

        // Return the list up the recursion chain, collecting all containee workflows
        return containeeWorkflows;
    }


    private void put(final ModifiableValueMap mvm, final String key, final Object value) {
        if (value != null) {
            if (value instanceof String) {
                if (StringUtils.isBlank((String) value)) {
                    log.debug("{} is a blank String", key);
                    // Value is a empty String; Don't save as MVM doesn't like this.
                    return;
                } else {
                    log.debug("{} is NOT a blank String", key);
                }
            } else {
                log.debug("{} is NOT a String", key);
            }

            // Not null and not empty String
            mvm.put(key, value);
        }
    }

    private void copyProperties(final Map<String, Object> src, final ModifiableValueMap dest) {
        for (final Map.Entry<String, Object> entry : src.entrySet()) {
            if (!StringUtils.startsWithAny(entry.getKey(), new String[]{ "jcr:", "sling:" })) {
                try {
                    if (entry.getValue() != null) {
                        put(dest, entry.getKey(), entry.getValue());
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Error copying property with value [ {} : {} ]", entry.getKey(), entry.getValue());
                    log.warn("Skipping property copying error...", e);
                }
            }
        }
    }

    private Resource getOrCreateResource(final Resource parent, final String nodeName) throws RepositoryException {
        final Node node = JcrUtils.getOrAddNode(parent.adaptTo(Node.class), nodeName, JcrConstants.NT_UNSTRUCTURED);
        return parent.getResourceResolver().getResource(node.getPath());
    }

    private void order(final Resource resource) throws RepositoryException {
        final Set<String> names = new TreeSet<String>(Collections.reverseOrder());
        final Iterator<Resource> children = resource.getChildren().iterator();

        while (children.hasNext()) {
            names.add(children.next().getName());
        }

        JcrUtil.setChildNodeOrder(resource.adaptTo(Node.class),
                names.toArray(new String[names.size()]));

        if (Constants.PATH_WORKFLOW_AUDIT.equals(resource.getPath())) {
            return;
        } else {
            this.order(resource.getParent());
        }
    }

    private Resource getOrCreateAuditResource(final ResourceResolver resourceResolver,
                                              final Workflow workflow) throws RepositoryException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");

        final String path = Constants.PATH_WORKFLOW_AUDIT
                + "/"
                + simpleDateFormat.format(workflow.getTimeStarted())
                + "/"
                + Text.getName(workflow.getId(), true);

        final Node node = JcrUtils.getOrCreateByPath(path,
                Constants.NT_SLING_ORDERED_FOLDER,
                Constants.NT_SLING_ORDERED_FOLDER,
                resourceResolver.adaptTo(Session.class), true);

        return resourceResolver.getResource(node.getPath());
    }

    private ResourceResolver getResourceResolver(Session session) throws LoginException {
        return resourceResolverFactory.getResourceResolver(Collections.<String, Object>singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
                session));
    }

    private void save(final ResourceResolver resourceResolver) throws PersistenceException {
        if (resourceResolver.hasChanges()) {
            final long start = System.currentTimeMillis();
            resourceResolver.commit();
            log.debug("Saved Workflow Audit in [ {} ] ms", System.currentTimeMillis() - start);
        }
    }

    private boolean isHarvested(final ResourceResolver resourceResolver, final String workflowId) {
        final Resource resource = resourceResolver.getResource(workflowId);

        if(resource != null) {
            final ValueMap properties = resource.adaptTo(ValueMap.class);
            return properties.get(Constants.PN_IS_HARVESTED, false);
        } else {
            log.warn("Could not find resource for Workflow Id [ {} ]", workflowId);
        }

         return false;
    }

    private void setHarvested(final ResourceResolver resourceResolver, final String workflowId) {
        final Resource resource = resourceResolver.getResource(workflowId);

        if(resource != null) {
            final ModifiableValueMap properties = resource.adaptTo(ModifiableValueMap.class);
            properties.put(Constants.PN_IS_HARVESTED, true);
        } else {
            log.warn("Could not find resource for Workflow Id [ {} ]", workflowId);
        }
    }
}
