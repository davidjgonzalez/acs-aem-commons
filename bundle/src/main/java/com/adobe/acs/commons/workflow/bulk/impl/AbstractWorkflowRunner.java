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

package com.adobe.acs.commons.workflow.bulk.impl;

import com.day.cq.workflow.WorkflowException;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Calendar;
import java.util.Map;

import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_INTERVAL;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_JOB_NAME;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_STATE;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.KEY_STOPPED_AT;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.STATE_STOPPED;
import static com.adobe.acs.commons.workflow.bulk.BulkWorkflowEngine.STATE_STOPPED_DEACTIVATED;

public abstract class AbstractWorkflowRunner {
    private static final Logger log = LoggerFactory.getLogger(AbstractWorkflowRunner.class);

    public abstract void start(final Resource resource);

    protected abstract Map<String, String> process(final Resource resource, String workflowModel) throws
            WorkflowException, PersistenceException, RepositoryException;

    protected abstract void complete(final Resource resource) throws PersistenceException;


    /**
     * {@inheritDoc}
     */
    @Override
    public final void resume(final Resource resource) {
        this.start(resource);
        log.info("Resumed bulk workflow for [ {} ]", resource.getPath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void resume(final Resource resource, final long interval) throws PersistenceException {
        final ModifiableValueMap properties = resource.adaptTo(ModifiableValueMap.class);
        properties.put(KEY_INTERVAL, interval);
        resource.getResourceResolver().commit();

        this.start(resource);
        log.info("Resumed bulk workflow for [ {} ] with new interval [ {} ]", resource.getPath(), interval);
    }

    /**
     * Stops the bulk workflow process using the user initiated stop state.
     *
     * @param resource the jcr:content configuration resource
     * @throws PersistenceException
     */
    @Override
    public final void stop(final Resource resource) throws PersistenceException {
        this.stop(resource, STATE_STOPPED);
    }

    /**
     * Stops the bulk workflow process using the OSGi Component deactivated stop state.
     * <p/>
     * Allows the system to know to resume this when the OSGi Component is activated.
     *
     * @param resource the jcr:content configuration resource
     * @throws PersistenceException
     */
    private void stopDeactivate(final Resource resource) throws PersistenceException {
        this.stop(resource, STATE_STOPPED_DEACTIVATED);
    }

    /**
     * Stops the bulk workflow process.
     *
     * @param resource the jcr:content configuration resource
     * @throws PersistenceException
     */
    private void stop(final Resource resource, final String state) throws PersistenceException {
        final ModifiableValueMap properties = resource.adaptTo(ModifiableValueMap.class);
        final String jobName = properties.get(KEY_JOB_NAME, String.class);

        log.debug("Stopping job [ {} ]", jobName);

        if (StringUtils.isNotBlank(jobName)) {
            scheduler.removeJob(jobName);
            jobs.remove(resource.getPath());

            log.info("Bulk Workflow Manager stopped for [ {} ]", jobName);

            properties.put(KEY_STATE, state);
            properties.put(KEY_STOPPED_AT, Calendar.getInstance());

            resource.getResourceResolver().commit();
        } else {
            log.error("Trying to stop a job without a name from Bulk Workflow Manager resource [ {} ]",
                    resource.getPath());
        }
    }



}
