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


import org.apache.felix.scr.annotations.sling.SlingServlet;
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
import java.util.Date;

@SlingServlet(
        label = "ACS AEM Commons - Property Manager - Downloads Servlet",
        description = "...",
        methods = "GET",
        resourceTypes = "acs-commons/components/utilities/bulk-property-manager",
        selectors = DownloadsServlet.TYPE,
        extensions = "json"
)
public class DownloadsServlet extends SlingSafeMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(DownloadsServlet.class);

    public static final String TYPE = "downloads";

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        final JSONArray jsonArray = new JSONArray();

        final Resource downloads = request.getResource().getChild("downloads");

        if(downloads != null) {
            for(final Resource file : downloads.getChildren()) {
                if(!file.isResourceType("nt:file")) {
                    // Only care about nt:files
                    continue;
                }

                final ValueMap properties = file.adaptTo(ValueMap.class);
                final JSONObject json = new JSONObject();

                try {
                    json.put("path", file.getPath());
                    json.put("createdAt", properties.get("jcr:created", new Date()).getTime());
                    json.put("createdBy", properties.get("jcr:createdBy", "unknown"));

                    jsonArray.put(json);
                } catch (JSONException e) {
                    log.error("Unable to create Downloads entry for [ {} ]", file.getPath());
                }
            }
        }

        response.setHeader("Content-Type", "application/json");
        response.getWriter().print(jsonArray.toString());
    }
}