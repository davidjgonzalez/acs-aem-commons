package com.adobe.acs.commons.workflow.audit.impl;

import com.adobe.acs.commons.workflow.audit.WorkflowAuditManager;
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
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
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
    public void audit(final Workflow workflow, final WorkflowSession workflowSession) throws WorkflowException,
            RepositoryException, PersistenceException, LoginException {

        ResourceResolver resourceResolver = null;

        try {
            resourceResolver = this.getResourceResolver(workflowSession.getSession());

            final List<HistoryItem> history = workflowSession.getHistory(workflow);

            final Resource audit = getOrCreateAuditResource(resourceResolver, workflow);
            final ModifiableValueMap auditProperties = audit.adaptTo(ModifiableValueMap.class);
            final Calendar cal = Calendar.getInstance();

            log.debug("Processing audit entry [ {} ]", audit.getPath());

            // Workflow Data
            put(auditProperties, "sling:resourceType", Constants.RT_WORKFLOW_INSTANCE_AUDIT);

            put(auditProperties, "payload", workflow.getWorkflowData().getPayload());
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
                put(auditProperties, "timeStarted", cal);
            }

            if (workflow.getTimeEnded() != null) {
                cal.setTime(workflow.getTimeEnded());
                put(auditProperties, "timedEnded", cal);
            }

            // Workflow Metadata
            this.copyProperties(workflow.getMetaDataMap(), auditProperties);

            // History
            for (final HistoryItem item : history) {
                final String itemId = String.valueOf(item.getDate().getTime());

                if (audit.getChild(itemId) == null) {
                    final Resource itemResource = getOrCreateResource(audit, itemId);
                    final ModifiableValueMap itemProperties = itemResource.adaptTo(ModifiableValueMap.class);

                    // History WorkItem
                    put(itemProperties, "sling:resourceType", Constants.RT_WORKFLOW_ITEM_AUDIT);

                    put(itemProperties, "assignee", item.getWorkItem().getCurrentAssignee());
                    put(itemProperties, "workitemId", item.getWorkItem().getId());
                    put(itemProperties, "stepTitle", item.getWorkItem().getNode().getTitle());

                    put(itemProperties, "stepDescription", item.getWorkItem().getNode().getDescription());
                    put(itemProperties, "stepType", item.getWorkItem().getNode().getType());
                    put(itemProperties, "stepId", item.getWorkItem().getNode().getId());

                    if (item.getWorkItem().getTimeStarted() != null) {
                        cal.setTime(item.getWorkItem().getTimeStarted());
                        put(auditProperties, "timeStarted", cal);
                    }

                    if (item.getWorkItem().getTimeEnded() != null) {
                        cal.setTime(item.getWorkItem().getTimeEnded());
                        put(auditProperties, "timedEnded", cal);
                    }

                    // History Item
                    put(itemProperties, "comment", item.getComment());
                    put(itemProperties, "action", item.getAction());
                    put(itemProperties, "userId", item.getUserId());

                    if (item.getDate() != null) {
                        cal.setTime(item.getDate());
                        put(itemProperties, "date", cal);
                    }

                    final Resource metadataResource = getOrCreateResource(itemResource, "metadata");
                    final ModifiableValueMap metadataProperties = metadataResource.adaptTo(ModifiableValueMap.class);

                    this.copyProperties(item.getWorkItem().getMetaDataMap(), metadataProperties);
                }
            }

            this.order(audit);
            this.save(resourceResolver);
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
    }

    private void put(final ModifiableValueMap mvm, final String key, final Object value) {
        if (value != null) {
            if (value instanceof String) {
                if (StringUtils.isBlank((String) value)) {
                    // Value is a empty String; Don't save as MVM doesn't like this.
                    return;
                }
            }

            // Not null and not empty String
            mvm.put(key, value);
        }
    }

    private void copyProperties(final Map<String, Object> src, final ModifiableValueMap dest) {
        for (final Map.Entry<String, Object> entry : src.entrySet()) {
            if (!StringUtils.startsWithAny(entry.getKey(), new String[]{ "jcr:", "sling:" })) {
                try {
                    put(dest, entry.getKey(), entry.getValue());
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

        if (Constants.ROOT_PATH.equals(resource.getPath())) {
            return;
        } else {
            this.order(resource.getParent());
        }
    }


    private Resource getOrCreateAuditResource(final ResourceResolver resourceResolver,
                                              final Workflow workflow) throws RepositoryException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");

        final String path = Constants.ROOT_PATH
                + "/"
                + simpleDateFormat.format(workflow.getTimeStarted())
                + "/"
                + Text.getName(workflow.getId(), true);

        final Node node = JcrUtils.getOrCreateByPath(path,
                "sling:OrderedFolder",
                "sling:OrderedFolder",
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
}
