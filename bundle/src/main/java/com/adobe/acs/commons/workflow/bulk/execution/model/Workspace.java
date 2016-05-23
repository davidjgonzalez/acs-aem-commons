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

import com.adobe.acs.commons.workflow.bulk.execution.BulkWorkflowRunner;
import com.adobe.acs.commons.workflow.bulk.execution.impl.Status;
import com.adobe.acs.commons.workflow.bulk.execution.impl.SubStatus;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


/**
 * jcr:content/workspace@activePayloadGroups=[...]
 * jcr:content/workspace@activePayloads=[...]
 */

@Model(adaptables = Resource.class)
public class Workspace {
    private static final Logger log = LoggerFactory.getLogger(Workspace.class);

    public static final String PN_ACTIVE_PAYLOAD_GROUPS = "activePayloadGroups";

    public static final String PN_ACTIVE_PAYLOADS = "activePayloads";

    public static final String NN_WORKSPACE = "workspace";

    private static final String PN_INITIALIZED = "initialized";

    private static final String PN_COMPLETED_AT = "completedAt";

    private static final String PN_COUNT_COMPLETE = "completeCount";

    private static final String PN_COUNT_FAILURE = "failCount";

    private static final String PN_COUNT_TOTAL = "totalCount";

    private static final String PN_STARTED_AT = "startedAt";

    private static final String PN_STATUS = "status";

    private static final String PN_SUB_STATUS = "subStatus";

    private static final String PN_STOPPED_AT = "stoppedAt";

    private Resource resource;

    private ModifiableValueMap properties;

    private BulkWorkflowRunner runner;

    private Config config;

    @Inject
    private List<BulkWorkflowRunner> runners;

    @Inject
    @Optional
    private String jobName;

    @Inject
    private ResourceResolver resourceResolver;

    @Inject
    @Default(values = {})
    private String[] activePayloads;

    @Inject
    @Default(values = {})
    private String[] activePayloadGroups;

    @Inject
    @Default(values = "NOT_STARTED")
    private String status;

    @Inject
    @Optional
    private String subStatus;

    @Inject
    @Default(booleanValues = false)
    private boolean initialized;

    @Inject
    @Default(intValues = 0)
    private int totalCount;

    @Inject
    @Default(intValues = 0)
    private int completeCount;

    @Inject
    @Default(intValues = 0)
    private int failCount;

    @Inject
    @Optional
    private Calendar startedAt;

    @Inject
    @Optional
    private Calendar stoppedAt;

    @Inject
    @Optional
    private Calendar completedAt;

    public Workspace(Resource resource) {
        this.resource = resource;
        this.properties = resource.adaptTo(ModifiableValueMap.class);
        this.jobName = "acs-commons@bulk-workflow-execution:/" + this.resource.getPath();
    }

    @PostConstruct
    protected void activate() throws Exception {
        this.config = resource.getParent().adaptTo(Config.class);

        for(BulkWorkflowRunner candidate : runners) {
            if (StringUtils.equals(this.config.getRunnerType(), candidate.getClass().getCanonicalName())) {
                runner = candidate;
                break;
            }
        }
    }

    /**
     * Getters
     **/
    public Calendar getCompletedAt() {
        return completedAt;
    }

    public int getCompleteCount() {
        return completeCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public String getJobName() {
        return jobName;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public BulkWorkflowRunner getRunner() {
        return runner;
    }

    public Calendar getStartedAt() {
        return startedAt;
    }

    public Status getStatus() {
        // Refresh state before getting the status.
        // Note, this gets the value from the session state, and not the cached Sling Model value as this value can change over the life of the SlingModel.
        resourceResolver.refresh();
        status = resource.getValueMap().get(PN_STATUS, Status.NOT_STARTED.toString());

        return EnumUtils.getEnum(Status.class, status);
    }

    public SubStatus getSubStatus() {
        // Refresh state before getting the status.
        // Note, this gets the value from the session state, and not the cached Sling Model value as this value can change over the life of the SlingModel.
        resourceResolver.refresh();
        subStatus = resource.getValueMap().get(PN_SUB_STATUS, String.class);

        if (subStatus != null) {
            return EnumUtils.getEnum(SubStatus.class, subStatus);
        } else {
            return null;
        }
    }

    public Calendar getStoppedAt() {
        return stoppedAt;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isResumable() {
        return Status.STOPPED.equals(getStatus()) && SubStatus.DEACTIVATED.equals(getSubStatus());
    }

    public boolean isRunning() {
        return Status.RUNNING.equals(getStatus());
    }

    public boolean isStopped() {
        return Status.STOPPED.equals(getStatus());
    }

    public boolean isStopping() {
        return Status.RUNNING.equals(getStatus()) && SubStatus.STOPPING.equals(getSubStatus());
    }

    public Config getConfig() {
        return config;
    }

    public ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    public boolean isActive(PayloadGroup payloadGroup) {
        return ArrayUtils.contains(activePayloadGroups, payloadGroup.getPath());
    }

    /**
     * Setters
     */
    public void setStatus(Status status) {
        this.status = status.toString();
        properties.put(PN_STATUS, this.status);
        // Clear subStatus
        subStatus = null;
        properties.remove(PN_SUB_STATUS);
    }

    public void setStatus(Status status, SubStatus subStatus) {
        setStatus(status);
        if (subStatus != null) {
            this.subStatus = subStatus.toString();
            properties.put(PN_SUB_STATUS, this.subStatus);
        }
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
        properties.put(PN_INITIALIZED, this.initialized);
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
        properties.put(PN_COUNT_TOTAL, this.totalCount);
    }

    public void setStartedAt(Calendar startedAt) {
        this.startedAt = startedAt;
        properties.put(PN_STARTED_AT, this.startedAt);
    }

    public void setStoppedAt(Calendar stoppedAt) {
        this.stoppedAt = stoppedAt;
        properties.put(PN_STOPPED_AT, this.stoppedAt);
    }

    public void setCompletedAt(Calendar completedAt) {
        this.completedAt = completedAt;
        properties.put(PN_COMPLETED_AT, this.completedAt);
    }

    public void incrementCompleteCount() {
        this.completeCount++;
        properties.put(PN_COUNT_COMPLETE, this.completeCount);
    }

    public void incrementFailCount() {
        this.failCount++;
        properties.put(PN_COUNT_FAILURE, this.failCount);
    }

    /**
     * Internal Implementation Details
     **/

    public void addActivePayload(Payload payload) {
        if (!ArrayUtils.contains(activePayloads, payload.getPath())) {
            activePayloads = (String[]) ArrayUtils.add(activePayloads, payload.getPath());
            properties.put(PN_ACTIVE_PAYLOADS, activePayloads);

            addActivePayloadGroup(payload.getPayloadGroup());
        }
    }

    public void removeActivePayload(Payload payload) {
        if (ArrayUtils.contains(activePayloads, payload.getPath())) {
            activePayloads = (String[]) ArrayUtils.removeElement(activePayloads, payload.getPath());
            properties.put(PN_ACTIVE_PAYLOADS, activePayloads);
        }
    }

    /**
     * @return a list of the payloads that are being actively processed by bulk workflow manager.
     */
    public List<Payload> getActivePayloads() {
        List<Payload> payloads = new ArrayList<Payload>();

        for(String path : activePayloads) {
            Resource r = resourceResolver.getResource(path);
            if(r != null) {
                Payload p = r.adaptTo(Payload.class);
                if(p != null) {
                    payloads.add(p);
                }
            }
        }

        return payloads;
    }

    /**
     * @return a list of the payload groups that have atleast 1 payload being process by bulk workflow manager.
     */
    public List<PayloadGroup> getActivePayloadGroups() {
        List<PayloadGroup> payloadGroups = new ArrayList<PayloadGroup>();

        if (activePayloadGroups != null) {
            for (String path : activePayloadGroups) {
                Resource r = resourceResolver.getResource(path);
                if (r == null) {
                    continue;
                }
                PayloadGroup pg = r.adaptTo(PayloadGroup.class);
                if (pg == null) {
                    continue;
                }
                payloadGroups.add(pg);
            }
        }

        return payloadGroups;
    }

    /**
     * Adds the payload group to the list of active payload groups.
     * @param payloadGroup the payload group to add as active
     */
    public void addActivePayloadGroup(PayloadGroup payloadGroup) {
        if (payloadGroup != null && !ArrayUtils.contains(activePayloadGroups, payloadGroup.getPath())) {
            activePayloadGroups = (String[]) ArrayUtils.add(activePayloadGroups, payloadGroup.getPath());
            properties.put(PN_ACTIVE_PAYLOAD_GROUPS, activePayloadGroups);
        }
    }

    /**
     * Removes the payload group from the list of active payload groups.
     * @param payloadGroup the payload group to remove from the active list.
     */
    public void removeActivePayloadGroup(PayloadGroup payloadGroup) {
        if (payloadGroup != null && ArrayUtils.contains(activePayloadGroups, payloadGroup.getPath())) {
            activePayloadGroups = (String[]) ArrayUtils.removeElement(activePayloadGroups, payloadGroup.getPath());
            properties.put(PN_ACTIVE_PAYLOAD_GROUPS, activePayloadGroups);
        }
    }

    /**
     * Commit the changes for this bulk workflow manager execution.
     * @throws PersistenceException
     */
    public void commit() throws PersistenceException {
        config.commit();
    }

}