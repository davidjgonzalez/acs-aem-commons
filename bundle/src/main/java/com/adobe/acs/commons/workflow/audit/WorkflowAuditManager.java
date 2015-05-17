package com.adobe.acs.commons.workflow.audit;

import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;

import javax.jcr.RepositoryException;
import java.util.List;

public interface WorkflowAuditManager {

    List<String> audit(Workflow workflow, WorkflowSession workflowSession) throws WorkflowException,
            RepositoryException, PersistenceException, LoginException;

    void link(Workflow originWorkflow, Workflow containeeWorkflow, WorkflowSession workflowSession) throws LoginException,
            RepositoryException, PersistenceException;
}
