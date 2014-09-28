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

package com.adobe.acs.commons.content.properties.bulk.impl.servlets;


import com.adobe.acs.commons.content.properties.bulk.impl.ResultsUtil;
import org.apache.commons.lang.ArrayUtils;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@SlingServlet(
        label = "ACS AEM Commons - Property Manager - Results Servlet",
        description = "...",
        methods = "GET",
        resourceTypes = "acs-commons/components/utilities/bulk-property-manager",
        selectors = { "results", "dry-run-results" },
        extensions = "json"
)
public class ResultsServlet extends SlingSafeMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(ResultsServlet.class);

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        final JSONArray jsonArray = new JSONArray();

        final boolean dryRun = ArrayUtils.contains(request.getRequestPathInfo().getSelectors(),
                "dry-run-results");

        final Resource results = ResultsUtil.getResultResource(request.getResource(), dryRun);


        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM dd YYYY, h:mm:ss a");

        if(results != null) {
            for(final Resource result : results.getChildren()) {
                final JSONObject json = new JSONObject();

                if(!result.isResourceType(JcrConstants.NT_UNSTRUCTURED)) {
                    // Only care about nt:unstructured result containers
                    continue;
                }

                final ValueMap resultProperties = result.adaptTo(ValueMap.class);

                try {
                    json.put("operation", resultProperties.get("operation", "unknown"));
                    json.put("success", resultProperties.get("success", 0));
                    json.put("error", resultProperties.get("error", 0));
                    json.put("noop", resultProperties.get("noop", 0));
                    json.put("total", resultProperties.get("total", 0));
                    json.put("path", result.getPath());
                    json.put("name", result.getName());

                } catch (JSONException e) {
                    log.error("Unable to create Results node at [ {} ]", result.getPath());
                }

                final Resource file = result.getChild(result.getName() + ".csv");
                if(file == null || !file.isResourceType(JcrConstants.NT_FILE)) {
                    // Only care about nt:files
                    continue;
                }

                final ValueMap fileProperties = file.adaptTo(ValueMap.class);

                try {
                    json.put("fileName", file.getName());
                    json.put("filePath", file.getPath());
                    json.put("createdAt", simpleDateFormat.format(fileProperties.get(JcrConstants.JCR_CREATED,
                            new Date()).getTime()));
                    json.put("createdBy", fileProperties.get("jcr:createdBy", "unknown"));

                    jsonArray.put(json);
                } catch (JSONException e) {
                    log.error("Unable to create Results CSV file at [ {} ]", file.getPath());
                }
            }
        }

        response.setHeader("Content-Type", "application/json");
        response.getWriter().print(jsonArray.toString());
    }
}