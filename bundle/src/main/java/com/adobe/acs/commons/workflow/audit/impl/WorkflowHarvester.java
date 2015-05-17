package com.adobe.acs.commons.workflow.audit.impl;

import com.adobe.acs.commons.workflow.audit.WorkflowAuditManager;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowService;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.AbstractResourceVisitor;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.discovery.TopologyEvent;
import org.apache.sling.discovery.TopologyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.HashSet;
import java.util.List;

@Component(
        label = "ACS AEM Commons - Workflow Audit Harvester",
        metatype = true,
        policy = ConfigurationPolicy.REQUIRE
)
@Properties({
        @Property(
                label = "Cron expression defining when this Scheduled Service will run",
                description = "[every minute = 0 * * * * ?] Visit www.cronmaker.com to generate cron expressions.",
                name = "scheduler.expression",
                value = "0 * * * * ?"
        ),
        @Property(
                label = "Allow concurrent executions",
                description = "Allow concurrent executions of this Scheduled Service. This is almost always false.",
                name = "scheduler.concurrent",
                propertyPrivate = true,
                boolValue = false
        )
})

@Service
public class WorkflowHarvester implements Runnable, TopologyEventListener {
    private final Logger log = LoggerFactory.getLogger(WorkflowHarvester.class);

    @Reference
    private WorkflowService workflowService;

    @Reference
    private WorkflowAuditManager workflowAuditManager;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private boolean isLeader = false;

    @Override
    public final void run() {
        if (!isLeader) {
            log.debug("isLeader: {}", isLeader);
            return;
        }

        // Scheduled service logic, only run on the Master
        ResourceResolver adminResourceResolver = null;
        try {
            // Be careful not to leak the adminResourceResolver
            adminResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);

            long start = System.currentTimeMillis();

            this.harvest(adminResourceResolver);

            log.info("Completed Workflow Harvest in {} ms", System.currentTimeMillis() - start);

            if (adminResourceResolver.hasChanges()) {
                adminResourceResolver.commit();
            }

        } catch (LoginException e) {
            log.error("Error obtaining the admin resource resolver.", e);
        } catch (PersistenceException e) {
            log.error("Error persisting changes.", e);
        } finally {
            // ALWAYS close resolvers you open
            if (adminResourceResolver != null) {
                adminResourceResolver.close();
            }
        }
    }

    private void harvest(final ResourceResolver resourceResolver) {
        final Resource root = resourceResolver.getResource(Constants.PATH_WORKFLOW_INSTANCES);
        final WorkflowInstanceResourceVisitor harvester = new WorkflowInstanceResourceVisitor();

        harvester.accept(root);
    }

    private List<String> harvest(Resource workflowResource) throws WorkflowException, RepositoryException,
            LoginException, PersistenceException {


        final WorkflowSession wfSession = workflowService.getWorkflowSession(
                workflowResource.getResourceResolver().adaptTo(Session.class));

        final Workflow workflow = wfSession.getWorkflow(workflowResource.getPath());

        log.trace("Harvesting workflow [ {} ]", workflowResource.getPath());

        final List<String> harvestedWorkflowIds = workflowAuditManager.audit(workflow, wfSession);

        // If workflow is complete, market this WF as harvested
        if (!workflow.isActive()) {
            final ModifiableValueMap modifiableValueMap = workflowResource.adaptTo(ModifiableValueMap.class);
            modifiableValueMap.put(Constants.PN_IS_HARVESTED, true);
        }

        return harvestedWorkflowIds;
    }



    /**
     * Topology Aware Methods *
     */

    @Override
    public void handleTopologyEvent(final TopologyEvent event) {
        if (event.getType() == TopologyEvent.Type.TOPOLOGY_CHANGED
                || event.getType() == TopologyEvent.Type.TOPOLOGY_INIT) {
            this.isLeader = event.getNewView().getLocalInstance().isLeader();
        }
    }


    /**
     *  Walks the AEM Workflow Instances tree harvesting WF data (/etc/workflow/instances)
     */
    private class WorkflowInstanceResourceVisitor extends AbstractResourceVisitor {

        private HashSet<String> harvestedWorkflowIds = new HashSet<String>();

        @Override
        public void accept(Resource resource) {
            final Node node = resource.adaptTo(Node.class);

            try {
                if (node.isNodeType(Constants.NT_CQ_WORKFLOW) || node.isNodeType(Constants.NT_SLING_FOLDER)) {
                    log.trace("Visiting [ {} ]", resource.getPath());
                    super.accept(resource);
                }
            } catch (RepositoryException e) {
                log.error("Could not check JCR Primary Type for [ {} ]", resource.getPath(), e);
            }
        }

        @Override
        protected void visit(final Resource resource) {
            final ValueMap properties = resource.adaptTo(ValueMap.class);

            if (!resource.isResourceType(Constants.RT_CQ_WORKFLOW_INSTANCE)) {
                return;
            } else if (properties.get(Constants.PN_IS_HARVESTED, false)) {
                // Workflow has been fully harvested; skip to next
                return;
            } if (properties.get(Constants.PN_IS_CONTAINEE, false)) {
                // Is a containee resource; these will be harvested by the origin workflow
                return;
            }

            try {
                harvestedWorkflowIds.addAll(harvest(resource));
            } catch (Exception e) {
                log.error("Error harvesting workflow for [ {} ]", resource.getPath());
            }
        }
    }
}
