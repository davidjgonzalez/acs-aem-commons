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

package com.adobe.acs.commons.workflow.bulk.impl.runners;

import com.adobe.acs.commons.workflow.bulk.BulkWorkflowRunner;
import com.day.cq.workflow.WorkflowException;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Map;

@Component
@Service
public class SyntheticWorkflowRunnerImpl extends AbstractWorkflowRunner implements BulkWorkflowRunner {
    private static final Logger log = LoggerFactory.getLogger(SyntheticWorkflowRunnerImpl.class);


    @Override
    public void start(Resource resource) {

    }

    @Override
    protected Map<String, String> process(Resource resource, String workflowModel) throws WorkflowException, PersistenceException, RepositoryException {
        return null;
    }
}