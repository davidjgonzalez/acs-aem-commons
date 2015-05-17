package com.adobe.acs.commons.workflow.audit.impl;

import com.adobe.acs.commons.workflow.audit.WorkflowAuditManager;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.Workflow;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(
        label = "ACS AEM Commons - Workflow Audit Containee Process Step"
)
@Properties({
        @Property(
                label = "Workflow Label",
                name = "process.label",
                value = "ACS AEM Commons - Workflow Audit Containee Process",
                description = "This step is used to provide a audit link between this Workflow and a containing " +
                        "workflow.",
                propertyPrivate = true
        )
})
@Service
public class WorkflowAuditContaineeProcess implements WorkflowProcess {
    private static final Logger log = LoggerFactory.getLogger(WorkflowAuditContaineeProcess.class);

    @Reference
    private QueryBuilder queryBuilder;

    @Reference
    private WorkflowAuditManager workflowAuditManager;

    /**
     * The method called by the AEM Workflow Engine to perform Workflow work.
     *
     * @param workItem the work item representing the resource moving through the Workflow
     * @param workflowSession the workflow session
     * @param args arguments for this Workflow Process defined on the Workflow Model (PROCESS_ARGS, argSingle, argMulti)
     * @throws com.day.cq.workflow.WorkflowException when the Workflow Process step cannot complete. This will cause the WF to retry.
     */
    @Override
    public final void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap args) throws
            WorkflowException {

        try {
            final String parentInstanceId = this.findParentWorkflowId(workItem.getWorkflow().getId(), workflowSession);

            if(StringUtils.isNotBlank(parentInstanceId)) {
                final Workflow parentWorkflow = workflowSession.getWorkflow(parentInstanceId);

                if (parentWorkflow != null) {
                    workflowAuditManager.link(parentWorkflow, workItem.getWorkflow(), workflowSession);
                } else {
                    log.warn("Could not find a Workflow for parent Workflow Id [ {} ]", parentInstanceId);
                }
            } else {
                log.debug("Could not find a parent Workflow Id for [ {} ]", workItem.getWorkflow().getId());
            }

        } catch (Exception e) {
            log.error("Unable to complete processing the Workflow Process step", e);

            throw new WorkflowException("Unable to complete processing the Workflow Process step", e);
        }
    }

    /**
     * Search the workflowStack nodes until the workflowStack whose parentId is itself is found
     *
     * @param containeeWorkflowId
     * @param workflowSession
     * @return
     * @throws RepositoryException
     */
    private String findParentWorkflowId(String containeeWorkflowId, WorkflowSession workflowSession) throws
            RepositoryException {

        final Map<String, String> map = new HashMap<String, String>();

        map.put("path", Constants.PATH_WORKFLOW_INSTANCES);
        map.put("type", "cq:WorkflowStack");
        map.put("property", "containeeInstanceId");
        map.put("property.value", containeeWorkflowId);

        final Query query = queryBuilder.createQuery(PredicateGroup.create(map), workflowSession.getSession());
        final SearchResult result = query.getResult();

        final List<Hit> hits = result.getHits();

        if(hits.size() == 1) {
            //  Found the parent workflow stack; there should only ever be 0 or 1
            final Hit hit = hits.get(0);
            final ValueMap hitProperties = hit.getProperties();
            final String parentInstanceId = hitProperties.get("parentInstanceId", String.class);

            log.debug("{} starts with {}", hit.getPath(), parentInstanceId);

            // This is the origin workflow since the parentInstanceId owns this WF Stack
            log.debug("Found origin instance Id [ {} ]", parentInstanceId);
            return parentInstanceId;
        }

        log.info("Workflow [ {} ] was not executed in the context of a running Parent WF", containeeWorkflowId);

        return null;
    }
}