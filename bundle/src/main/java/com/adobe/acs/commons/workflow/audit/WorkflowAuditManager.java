package com.adobe.acs.commons.workflow.audit;

import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;

import javax.jcr.RepositoryException;

public interface WorkflowAuditManager {

    void audit(Workflow workflow, WorkflowSession workflowSession) throws WorkflowException,
            RepositoryException, PersistenceException, LoginException;

}
