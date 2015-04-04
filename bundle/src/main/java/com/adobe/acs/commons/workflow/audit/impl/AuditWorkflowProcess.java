package com.adobe.acs.commons.workflow.audit.impl;

import com.adobe.acs.commons.workflow.audit.WorkflowAuditManager;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        label = "ACS AEM Commons - Audit Workflow Process"
)
@Properties({
        @Property(
                name = Constants.SERVICE_DESCRIPTION,
                value = "ACS AEM Commons - Audit Workflow Process implementation.",
                propertyPrivate = true
        ),
        @Property(
                label = "Workflow Label",
                name = "process.label",
                value = "ACS AEM Commons - Audit Workflow Process",
                description = "Label which will appear in the AEM Workflow interface; This should be unique across "
                        + "Workflow Processes",
                propertyPrivate = true
        )
})
@Service
public class AuditWorkflowProcess implements WorkflowProcess {
    private static final Logger log = LoggerFactory.getLogger(AuditWorkflowProcess.class);

    @Reference
    private WorkflowAuditManager workflowAuditManager;

    /**
     * The method called by the AEM Workflow Engine to perform Workflow work.
     *
     * @param workItem the work item representing the resource moving through the Workflow
     * @param workflowSession the workflow session
     * @param args arguments for this Workflow Process defined on the Workflow Model (PROCESS_ARGS, argSingle, argMulti)
     * @throws WorkflowException when the Workflow Process step cannot complete. This will cause the WF to retry.
     */
    @Override
    public final void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap args) throws
            WorkflowException {

        try {

            workflowAuditManager.audit(workItem.getWorkflow(), workflowSession);

        } catch (Exception e) {
            log.error("Unable to complete processing the Workflow Process step", e);

            throw new WorkflowException("Unable to complete processing the Workflow Process step", e);
        }
    }

}