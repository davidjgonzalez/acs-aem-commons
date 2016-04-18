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

package com.adobe.acs.commons.workflow.bulk.execution.model;

import com.adobe.acs.commons.workflow.bulk.execution.impl.Status;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowService;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static com.day.cq.wcm.foundation.List.log;

@Model(adaptables = Resource.class)
public class Payload {

    private ModifiableValueMap properties;

    private Resource resource;

    @Inject
    private WorkflowService workflowService;

    @Inject
    @Default(values = "NOT_STARTED")
    private String status;

    @Inject
    private String path;

    @Inject
    @Optional
    private String workflowInstanceId;

    public Payload(Resource resource) {
        this.resource = resource;
    }

    @PostConstruct
    public void init() {
        properties = resource.adaptTo(ModifiableValueMap.class);
    }

    public ResourceResolver getResourceResolver() {
        return resource.getResourceResolver();
    }

    public Status getStatus() {
        return EnumUtils.getEnum(Status.class, status);
    }

    public void setStatus(Status status) {
        this.status = status.toString();
        properties.put("status", this.status);
    }

    public String getPayloadPath() {
        return path;
    }

    public String getPath() {
        return resource.getPath();
    }

    public void updateWith(Workflow workflow) throws PersistenceException {
        if (StringUtils.isBlank(workflowInstanceId)) {
            workflowInstanceId = workflow.getId();
            properties.put("workflowInstanceId", this.workflowInstanceId);
        } else if (!StringUtils.equals(workflowInstanceId, workflow.getId())) {
            throw new PersistenceException("Batch Entry workflow instance does not match [ " + workflowInstanceId + " ] vs [ " + workflow.getId() + "  ]");
        }

        if (!StringUtils.equals(status, workflow.getState())) {
            // Status is different, so update
            setStatus(EnumUtils.getEnum(Status.class, workflow.getState()));
        }
    }

    public PayloadGroup getPayloadGroup() {
        return resource.getParent().adaptTo(PayloadGroup.class);
    }

    public Workflow getWorkflow() throws WorkflowException {
        final WorkflowSession workflowSession =
                workflowService.getWorkflowSession(resource.getResourceResolver().adaptTo(Session.class));
        try {
            return workflowSession.getWorkflow(workflowInstanceId);
        } catch (WorkflowException e) {
            log.error("Could not get workflow with id [ {} ]", workflowInstanceId);
        }

        return null;
    }

    public boolean isOnboarded() {
        Status status = getStatus();
        return (status != null && !Status.NOT_STARTED.equals(status));
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("status", getStatus().toString());
        json.put("path", getPayloadPath());
        return json;
    }
}
