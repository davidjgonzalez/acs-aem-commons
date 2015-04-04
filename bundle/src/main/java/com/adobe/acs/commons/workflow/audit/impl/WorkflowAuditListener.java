package com.adobe.acs.commons.workflow.audit.impl;


import com.adobe.acs.commons.workflow.audit.WorkflowAuditManager;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowService;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.JobProcessor;
import org.apache.sling.event.jobs.JobUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

@Component(
        label = "ACS AEM Commons - Workflow Audit Event Handler",
        description = "Captures AEM Workflow events and stores them as custom Audit nodes.",
        immediate = true
)
@Properties({
        @Property(
                label = "Event Topics",
                value = { SlingConstants.TOPIC_RESOURCE_CHANGED },
                name = EventConstants.EVENT_TOPIC,
                propertyPrivate = true
        ),
        @Property(
                label = "Event Filters",
                value = "(" + SlingConstants.PROPERTY_RESOURCE_TYPE + "=cq/workflow/components/instance)",
                name = EventConstants.EVENT_FILTER,
                propertyPrivate = true
        )
})
@Service
public class WorkflowAuditListener implements JobProcessor, EventHandler {
    private Logger log = LoggerFactory.getLogger(WorkflowAuditListener.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private WorkflowAuditManager workflowAuditManager;

    @Reference
    private WorkflowService workflowService;

    @Override
    public void handleEvent(final Event event) {
        final String path = (String) event.getProperty(SlingConstants.PROPERTY_PATH);

        if (StringUtils.startsWith(path, "/etc/workflow/instances/")) {
            final String[] changedProperties = (String[]) event.getProperty(SlingConstants.PROPERTY_CHANGED_ATTRIBUTES);

            if (ArrayUtils.contains(changedProperties, "status")) {
                JobUtil.processJob(event, this);
            }
        }
    }

    @Override
    synchronized public boolean process(final Event event) {
        final String path = (String) event.getProperty(SlingConstants.PROPERTY_PATH);

        ResourceResolver resourceResolver = null;

        try {
            resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
            WorkflowSession workflowSession
                    = workflowService.getWorkflowSession(resourceResolver.adaptTo(Session.class));

            final Workflow workflow = workflowSession.getWorkflow(path);

            workflowAuditManager.audit(workflow, workflowSession);

        } catch (LoginException e) {
            log.error("Could not get Workflow Audit service account.", e);
            return false;
        } catch (PersistenceException e) {
            log.error("Could not save Audit entry for Workflow.", e);
            return false;
        } catch (RepositoryException e) {
            log.error("Could not create Audit entry for Workflow.", e);
            return false;
        } catch (WorkflowException e) {
            e.printStackTrace();
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }

        // Only return false if job processing failed and the job should be rescheduled
        return true;
    }
}
