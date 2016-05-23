/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2013 Adobe
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

package com.adobe.acs.commons.workflow.bulk.execution.impl.servlets;

import com.adobe.acs.commons.workflow.bulk.execution.BulkWorkflowEngine;
import com.adobe.acs.commons.workflow.bulk.execution.model.Config;
import com.adobe.acs.commons.workflow.bulk.execution.model.Payload;
import com.adobe.acs.commons.workflow.bulk.execution.model.Workspace;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * ACS AEM Commons - Bulk Workflow Manager - Status Servlet
 */
@SuppressWarnings("serial")
@SlingServlet(
        methods = {"GET"},
        resourceTypes = {BulkWorkflowEngine.SLING_RESOURCE_TYPE},
        selectors = {"status"},
        extensions = {"json"}
)
public class StatusServlet extends SlingAllMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(StatusServlet.class);

    private static final int DECIMAL_TO_PERCENT = 100;

    @Override
    protected final void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy hh:mm:ss aaa");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Config config = request.getResource().adaptTo(Config.class);
        Workspace workspace = config.getWorkspace();

        final JSONObject json = new JSONObject();

        try {
            json.put("initialized", workspace.isInitialized());
            json.put("status", workspace.getStatus());

            if (workspace.getSubStatus() != null) {
                json.put("subStatus", workspace.getSubStatus());
            }

            json.put("runnerType", config.getRunnerType());
            json.put("queryStatement", config.getQueryStatement());
            json.put("workflowModel", StringUtils.removeEnd(config.getWorkflowModelId(), "/jcr:content/model"));
            json.put("batchSize", config.getBatchSize());

            json.put("purgeWorkflow", config.isPurgeWorkflow());
            json.put("interval", config.getInterval());
            json.put("timeout", config.getTimeout());
            json.put("throttle", config.getThrottle());

            // Counts
            int remainingCount = workspace.getTotalCount() - (workspace.getCompleteCount() + workspace.getFailCount());
            json.put("totalCount", workspace.getTotalCount());
            json.put("completeCount", workspace.getCompleteCount());
            json.put("remainingCount", remainingCount);
            json.put("failCount", workspace.getFailCount());
            json.put("percentComplete", Math.round(((workspace.getTotalCount() - remainingCount) / (workspace.getTotalCount() * 1F)) * DECIMAL_TO_PERCENT));

            // Times
            if (workspace.getStartedAt() != null) {
                json.put("startedAt",sdf.format(workspace.getStartedAt().getTime()));
            }

            if (workspace.getStoppedAt() != null) {
                json.put("stoppedAt", sdf.format(workspace.getStoppedAt().getTime()));
            }

            if (workspace.getCompletedAt() != null) {
                json.put("completedAt", sdf.format(workspace.getCompletedAt().getTime()));
            }

            for (Payload payload : config.getWorkspace().getActivePayloads()) {
                json.accumulate("activePayloads", payload.toJSON());
            }

            response.getWriter().write(json.toString());

        } catch (JSONException e) {
            log.error("Could not collect Bulk Workflow status due to: {}", e);

            HttpErrorUtil.sendJSONError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not collect Bulk Workflow status.", e.getMessage());
        }
    }
}
