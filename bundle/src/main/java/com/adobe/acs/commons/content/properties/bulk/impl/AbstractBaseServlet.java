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

package com.adobe.acs.commons.content.properties.bulk.impl;


import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

abstract class AbstractBaseServlet extends SlingAllMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(AbstractBaseServlet.class);

    protected static final int DEFAULT_BATCH_SIZE = 1000;

    protected static final String REQUEST_PARAM_PARAMS = "params";

    protected static final String KEY_BATCH_SIZE = "batchSize";
    protected static final String KEY_QUERY = "query";


    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        try {
            final JSONObject jsonParams = new JSONObject(request.getParameter(REQUEST_PARAM_PARAMS));

            final String query = jsonParams.optString(KEY_QUERY, "");
            final int batchSize = jsonParams.optInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE);

            // Get command specific

            params.putAll(getParams(jsonParams));

            /* Process */

            final JSONObject jsonResponse = new JSONObject();

            final ResourceResolver resourceResolver = request.getResourceResolver();
            final Session session = resourceResolver.adaptTo(Session.class);
            final Iterator<Resource> resources = resourceResolver.findResources(query, Query.JCR_SQL2);

            int total = 0;
            int count = 0;
            while(resources.hasNext()) {
                total++;
                if(execute(resources.next(), params)) {
                    count++;
                }

                if(count != 0 && count % batchSize == 0) {
                    long start = System.currentTimeMillis();
                    session.save();
                    log.info("Saved batch of [ {} ] copy/move property changes in {} ms", batchSize,
                            System.currentTimeMillis() - start);
                }
            }

            if(count % batchSize != 0) {
                long start = System.currentTimeMillis();
                session.save();
                log.info("Saved batch of [ {} ] copy/move property changes in {} ms", count % batchSize,
                        System.currentTimeMillis() - start);
            }

            // Set to JSON

            jsonResponse.put("total", total);
            jsonResponse.put("count", count);

            response.setHeader("Content-Type", "application/json");
            response.getWriter().print(jsonResponse.toString());

        } catch (JSONException e) {
            log.error(e.getMessage());
            response.sendError(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error getting params");
        } catch (Exception e) {
            log.error(e.getMessage());
            response.sendError(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not process");
        }
    }

    abstract Map<String, Object> getParams(JSONObject json) throws JSONException;

    abstract boolean execute(Resource resource, ValueMap params);
}