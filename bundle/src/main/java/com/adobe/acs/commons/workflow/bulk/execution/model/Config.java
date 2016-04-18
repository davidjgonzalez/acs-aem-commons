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

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@Model(adaptables = Resource.class)
public class Config {
    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private Resource resource;

    @Inject
    @Default(values = "com.adobe.acs.commons.workflow.bulk.execution.impl.runners.AEMWorkflowRunnerImpl")
    private String runnerType;

    @Inject
    @Optional
    private String queryStatement;

    @Inject
    @Default(values = "querybuilder")
    private String queryType;

    @Inject
    @Default(intValues = 10)
    private int timeout;

    @Inject
    @Default(booleanValues = false)
    private boolean purgeWorkflow;

    @Inject
    @Default(intValues = 0)
    private int batchSize;

    @Inject
    @Default(intValues = 10)
    private int interval;

    @Inject
    @Default(intValues = 10)
    private int throttle;

    @Inject
    @Optional
    private String relativePath;

    @Inject
    @Optional
    private String workflowModel;

    private Workspace workspace;

    public Config(Resource resource) {
        this.resource = resource;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getThrottle() {
        return throttle;
    }

    public boolean isPurgeWorkflow() {
        return purgeWorkflow;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getInterval() {
        return interval;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getRunnerType() {
        return runnerType;
    }

    public String getWorkflowModelId() {
        return workflowModel;
    }

    public String getPath() {
        return resource.getPath();
    }

    public ResourceResolver getResourceResolver() {
        return this.resource.getResourceResolver();
    }

    public String getQueryStatement() {
        return queryStatement;
    }

    public String getQueryType() {
        return queryType;
    }

    public Resource getResource() {
        return resource;
    }

    public Workspace getWorkspace() {
        if (workspace == null) {
            workspace = this.resource.getChild(Workspace.NN_WORKSPACE).adaptTo(Workspace.class);
        }

        return workspace;
    }

    public void commit() throws PersistenceException {
        if (this.getResourceResolver().hasChanges()) {
            this.getResourceResolver().commit();
        }
    }
}



