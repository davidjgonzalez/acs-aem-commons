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
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowService;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.Workflow;
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
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
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

    public static final String PN_ACTIVE_PAYLOAD_GROUP_PATHS = "activePayloadGroups";

    public static final String PN_ACTIVE_PAYLOAD_PATHS = "activePayloads";

    public static final String NN_WORKSPACE = "workspace";

    private static final String PN_INITIALIZED = "initialized";

    private static final String PN_COMPLETED_AT = "completedAt";

    private static final String PN_COUNT_COMPLETE = "completeCount";

    private static final String PN_COUNT_FAILURE = "failureCount";

    private static final String PN_COUNT_TOTAL = "totalCount";

    private static final String PN_STARTED_AT = "startedAt";

    private static final String PN_STATUS = "status";

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
    private WorkflowService workflowService;

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
    private int failureCount;

    @Inject
    @Optional
    private Calendar startedAt;

    @Inject
    @Optional
    private Calendar stoppedAt;

    @Inject
    @Optional
    private Calendar completedAt;
    private String[] activePayloadGroupPaths;


    public Workspace(Resource resource) {
        this.resource = resource;
        this.properties = resource.adaptTo(ModifiableValueMap.class);
        this.config = resource.getParent().adaptTo(Config.class);
        this.jobName = "acs-commons@bulk-workflow-execution:/" + this.resource.getPath();
    }

    @PostConstruct
    protected void init() throws Exception {
        for(BulkWorkflowRunner candidateRunner : runners) {
            if (StringUtils.equals(config.getRunnerType(), candidateRunner.getClass().getCanonicalName())) {
                runner = candidateRunner;
                break;
            }
        }
    }

    /**
     * Operations
     **/

    /*

    public Payload onboardNextPayload() {
        long start = System.currentTimeMillis();

        List<PayloadGroup> payloadGroups = getActivePayloadGroups();

        for (PayloadGroup payloadGroup : payloadGroups) {
            Payload payload = payloadGroup.getNextPayload();

            if (payload != null && !payload.isOnboarded()) {
                // Onboard this payload as it hasnt been onboarded yet
                addActivePayload(payload);

                if (log.isDebugEnabled()) {
                    log.debug("Took {} ms to onboard next payload", System.currentTimeMillis() - start);
                }
                return payload;
            }
        }

        // No payloads in the active payload groups are eligible for onboarding

        PayloadGroup nextPayloadGroup = null;
        for (PayloadGroup payloadGroup : payloadGroups) {
            nextPayloadGroup = onboardNextPayloadGroup(payloadGroup);

            if (nextPayloadGroup != null) {
                Payload payload = nextPayloadGroup.getNextPayload();
                if (payload == null) {
                    // all done! empty group
                }

                addActivePayloadGroup(payloadGroup);
                addActivePayload(payload);

                if (log.isDebugEnabled()) {
                    log.debug("Took {} ms to onboard next payload", System.currentTimeMillis() - start);
                }

                return payload;
            } else {
                log.debug("Could not find a next payload group for [ {} ]", payloadGroup.getPath());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Took {} ms to onboard next payload", System.currentTimeMillis() - start);
        }
        return null;
    }

    public PayloadGroup onboardNextPayloadGroup(PayloadGroup payloadGroup) {
        // Assumes a next group should be onboarded
        // This method is not responsible for removing items from the activePayloadGroups

        if (payloadGroup == null) {
            return null;
        }

        PayloadGroup candidatePayloadGroup = payloadGroup.getNextPayloadGroup();

        if (candidatePayloadGroup == null) {
            // payloadGroup is the last! nothing to do!
            return null;
        } else if (ArrayUtils.contains(activePayloadGroups, candidatePayloadGroup.getPath())
                || candidatePayloadGroup.getNextPayload() == null) {
            // Already processing the next group, use *that* group's next group
            // OR there is nothing left in that group to process...

            // recursive call..
            return onboardNextPayloadGroup(candidatePayloadGroup);
        } else {
            // Found a good payload group! has atleast 1 payload that can be onboarded
            ArrayUtils.add(activePayloadGroups, payloadGroup.getPath());
            return candidatePayloadGroup;
        }
    }

    */

    /**
     * Getters
     **/
    public Calendar getCompletedAt() {
        return completedAt;
    }

    public int getCompleteCount() {
        return completeCount;
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
        resourceResolver.refresh();
        status = resource.getValueMap().get(PN_STATUS, Status.NOT_STARTED.toString());
        return EnumUtils.getEnum(Status.class, status);
    }

    public Calendar getStoppedAt() {
        return stoppedAt;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isResumable() {
        return Status.STOPPED_DEACTIVATED.equals(getStatus());
    }

    public boolean isRunning() {
        return Status.RUNNING.equals(getStatus());
    }

    public boolean isStopped() {
        return Status.STOPPED.equals(getStatus())
                || Status.STOPPED_DEACTIVATED.equals(getStatus())
                || Status.STOPPED_ERROR.equals(getStatus());
    }


    /**
     * Setters
     */
    public void setStatus(Status status) {
        this.status = status.toString();
        properties.put(PN_STATUS, this.status);
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
        properties.put(PN_INITIALIZED, initialized);
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
        properties.put(PN_COUNT_TOTAL, totalCount);
    }

    public void setStartedAt(Calendar startedAt) {
        this.startedAt = startedAt;
        properties.put(PN_STARTED_AT, startedAt);
    }

    public void setStoppedAt(Calendar stoppedAt) {
        this.stoppedAt = stoppedAt;
        properties.put(PN_STOPPED_AT, stoppedAt);
    }

    public void setCompletedAt(Calendar completedAt) {
        this.completedAt = completedAt;
        properties.put(PN_COMPLETED_AT, completedAt);
    }

    public void incrementComplete() {
        properties.put(PN_COUNT_COMPLETE, properties.get(PN_COUNT_COMPLETE, 0) + 1);
    }

    public void incrementFailure() {
        properties.put(PN_COUNT_FAILURE, properties.get(PN_COUNT_FAILURE, 0) + 1);
    }

    /**
     * Internal Implementation Details
     **/

    public void addActivePayload(Payload payload) {
        if (!ArrayUtils.contains(activePayloads, payload.getPath())) {
            activePayloads = (String[]) ArrayUtils.add(activePayloads, payload.getPath());
            properties.put(PN_ACTIVE_PAYLOAD_PATHS, activePayloads);

            addActivePayloadGroup(payload.getPayloadGroup());
        }
    }

    public void removeActivePayload(Payload payload) {
        if (ArrayUtils.contains(activePayloads, payload.getPath())) {
            activePayloads = (String[]) ArrayUtils.removeElement(activePayloads, payload.getPath());
            properties.put(PN_ACTIVE_PAYLOAD_PATHS, activePayloads);
        }
    }

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

    public void addActivePayloadGroup(PayloadGroup payloadGroup) {
        if (!ArrayUtils.contains(activePayloadGroups, payloadGroup.getPath())) {
            activePayloadGroups = (String[]) ArrayUtils.add(activePayloadGroups, payloadGroup.getPath());
            properties.put(PN_ACTIVE_PAYLOAD_GROUP_PATHS, activePayloadGroups);
        }
    }

    public void removeActivePayloadGroup(PayloadGroup payloadGroup) {
        if (ArrayUtils.contains(activePayloadGroups, payloadGroup.getPath())) {
            activePayloadGroups = (String[]) ArrayUtils.removeElement(activePayloadGroups, payloadGroup.getPath());
            properties.put(PN_ACTIVE_PAYLOAD_GROUP_PATHS, activePayloadGroups);
        }
    }

    public Config getConfig() {
        if (config == null) {
            config = this.resource.getParent().adaptTo(Config.class);
        }

        return config;
    }

    public ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    public void commit() throws PersistenceException {
        config.commit();
    }

    public void addActivePayloadPath(String path) {
        activePayloads = (String[]) ArrayUtils.add(activePayloads, path);
        properties.put(PN_ACTIVE_PAYLOAD_PATHS, activePayloads);
    }

    public void addActivePayloadGroupPath(String path) {
        activePayloadGroups = (String[]) ArrayUtils.add(activePayloadGroupPaths, path);
        properties.put(PN_ACTIVE_PAYLOAD_GROUP_PATHS, activePayloadGroupPaths);
    }

    public boolean isActive(PayloadGroup candidatePayloadGroup) {
        return ArrayUtils.contains(activePayloadGroups, candidatePayloadGroup.getPath());
    }
}