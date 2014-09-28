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


import com.adobe.acs.commons.content.properties.bulk.impl.NodeCollector;
import com.adobe.acs.commons.content.properties.bulk.impl.Result;
import com.adobe.acs.commons.content.properties.bulk.impl.ResultsUtil;
import com.day.text.Text;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

abstract class AbstractBaseServlet extends SlingAllMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(AbstractBaseServlet.class);

    protected static final int DEFAULT_BATCH_SIZE = 1000;

    protected static final String REQUEST_PARAM_PARAMS = "params";

    protected static final String KEY_BATCH_SIZE = "batchSize";

    protected static final String KEY_DRY_RUN = "dryRun";

    protected static final String KEY_QUERY = "query";

    protected static final String KEY_COLLECTION_MODE = "collectionMode";

    protected static final String KEY_QUERY_MODE = "queryMode";

    protected static final String KEY_RELATIVE_PATH = "relativePath";

    protected static final String KEY_SEARCH_PATH = "path";

    protected static final String KEY_NODE_TYPE = "nodeType";

    protected static final String KEY_PROPERTIES = "properties";

    protected static final String KEY_PROPERTIES_OPERAND = "propertiesOperand";

    protected static final String VALUE_QUERY_MODE_CONSTRUCTED = "constructed";

    protected static final String VALUE_COLLECTION_MODE_TRAVERSAL = "traversal";

    protected static final String KEY_OPERATION = "operation";

    /**
     * Abstract method used to get operation-specific parameters
     *
     * @param json the JSON object of request params
     * @return a Map of the relevant parameters
     * @throws JSONException
     */
    abstract Map<String, Object> getParams(JSONObject json) throws JSONException;

    /**
     * Abstract method used to process operation-specific behaviors.
     *
     * @param resource the resource whose properties will be effected
     * @param params function-specific parameters
     * @return a Status indicating what happened while processing this resource
     */
    abstract Result execute(Resource resource, ValueMap params);


    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        final ResourceResolver resourceResolver = request.getResourceResolver();

        try {
            // Get common params
            final ValueMap params = this.getParams(request);

            // Get command specific params
            params.putAll(getParams(new JSONObject(request.getParameter(REQUEST_PARAM_PARAMS))));

            // Get values after any overwrites are done by the function specific servlets

            final boolean dryRun = params.get(KEY_DRY_RUN, false);
            final String queryMode = params.get(KEY_QUERY_MODE, String.class);
            final String relativePath = params.get(KEY_RELATIVE_PATH, String.class);
            final int batchSize = params.get(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE);

            /* Get Qualifying Nodes */
            final Iterator<Node> nodes = getNodes(resourceResolver, params, queryMode);

            /* Response Headers */
            response.setHeader("Content-Type", "application/json");


            /* Process */
            final Resource resultResource = ResultsUtil.getResultResource(request.getResource(), dryRun);
            final Session session = resourceResolver.adaptTo(Session.class);
            final List<Result> results = new ArrayList<Result>();
            List<Result> batch = new ArrayList<Result>();

            int count = 0;
            while (nodes.hasNext()) {
                final Resource foundResource = resourceResolver.getResource(nodes.next().getPath());
                Resource resource = foundResource;

                // Relative path resolution

                if (StringUtils.isNotBlank(relativePath)) {
                    final Resource relativeResource = foundResource.getChild(relativePath);

                    if (relativeResource != null) {
                        resource = relativeResource;
                    } else {
                        results.add(new Result(Result.Status.RELATIVE_PATH_NOT_FOUND,
                                Text.makeCanonicalPath(foundResource.getPath() + "/" + relativePath)));

                        // Could not find a relative resource; so skip and move on
                        continue;
                    }
                }

                // Resource processing
                final Result result = execute(resource, params);

                if (Result.Status.SUCCESS.equals(result.getStatus())) {
                    batch.add(result);
                    count++;
                } else {
                    // Always store ERRORs and NOOps immediately
                    results.add(result);
                }

                batch = saveChanges(batchSize, session, results, batch, count, false);
            }

            saveChanges(batchSize, session, results, batch, count, true);

            // Set to JSON
            ResultsUtil.createResults(results, params, resultResource);
        } catch (JSONException e) {
            log.error("JSON Exception", e);
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("Error getting params");
        } catch (Exception e) {
            log.error("An error occurred", e);
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("Could not process " + e.getMessage());
        }
    }

    /**
     * Persists the changes to the repository
     *
     * @param batchSize
     * @param session
     * @param results
     * @param batch
     * @param count
     * @param force
     * @return
     */
    private List<Result> saveChanges(final int batchSize, final Session session, final List<Result> results,
                                     List<Result> batch, final int count, boolean force) {
        if (force || (count != 0 && count % batchSize == 0)) {
            long start = System.currentTimeMillis();
            try {
                session.save();
                log.info("Saved batch of [ {} ] property change operation in {} ms", batchSize,
                        System.currentTimeMillis() - start);
                results.addAll(batch);
            } catch(Exception e) {
                // Record batch as SAVE_ERROR
                results.addAll(ResultsUtil.convertToStatus(batch, Result.Status.ERROR));
            }

            batch = new ArrayList<Result>();
        }
        return batch;
    }

    /**
     * Gets the Node Iterator that represents the collections of nodes to apply the bulk property functions against.
     *
     * @param resourceResolver the resource resolver used to collect the nodes
     * @param params the params used to construct the collection
     * @param queryMode the way in which to collect the nodes
     * @return a Node Iterator representing all the nodes to process
     * @throws RepositoryException
     */
    private Iterator<Node> getNodes(final ResourceResolver resourceResolver, final ValueMap params, final String queryMode) throws RepositoryException {
        if (StringUtils.equals(queryMode, VALUE_QUERY_MODE_CONSTRUCTED)) {

            final String collectionMode = params.get(KEY_COLLECTION_MODE, String.class);

            if (StringUtils.equals(collectionMode, VALUE_COLLECTION_MODE_TRAVERSAL)) {
                log.trace("Executing constructed traversal");
                return NodeCollector.getNodesFromConstructedTraversal(resourceResolver,
                        params.get(KEY_SEARCH_PATH, "/dev/null"),
                        params.get(KEY_NODE_TYPE, String.class),
                        params.get(KEY_PROPERTIES_OPERAND, "OR"),
                        params.get(KEY_PROPERTIES, ValueMap.class));
            } else {
                log.trace("Executing constructed query");
                return NodeCollector.getNodesFromConstructedQuery(resourceResolver,
                        params.get(KEY_SEARCH_PATH, "/dev/null"),
                        params.get(KEY_NODE_TYPE, String.class),
                        params.get(KEY_PROPERTIES, ValueMap.class));
            }
        } else {
            log.trace("Executing raw query");
            return NodeCollector.getNodesFromRawQuery(resourceResolver, params.get(KEY_QUERY, ""));
        }
    }

    /**
     * Converts the Request parameters into a normalized ValueMap.
     *
     * @param request the request object
     * @return a ValueMap containing the normalized parameters
     * @throws JSONException
     */
    protected ValueMap getParams(SlingHttpServletRequest request) throws JSONException {
        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());

        final JSONObject jsonParams = new JSONObject(request.getParameter(REQUEST_PARAM_PARAMS));

        params.put(KEY_DRY_RUN, jsonParams.optBoolean(KEY_DRY_RUN, true));
        params.put(KEY_BATCH_SIZE, jsonParams.optInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE));

        params.put(KEY_QUERY_MODE, jsonParams.optString(KEY_QUERY_MODE, VALUE_QUERY_MODE_CONSTRUCTED));

        // Constructed Query

        if (StringUtils.equals(params.get(KEY_QUERY_MODE, String.class), VALUE_QUERY_MODE_CONSTRUCTED)) {
            JSONObject constructed = jsonParams.getJSONObject("constructed");

            params.put(KEY_COLLECTION_MODE, constructed.optString(KEY_COLLECTION_MODE, VALUE_COLLECTION_MODE_TRAVERSAL));

            if (!StringUtils.equals(VALUE_COLLECTION_MODE_TRAVERSAL, params.get(KEY_COLLECTION_MODE, String.class))) {
                if (StringUtils.isNotBlank(constructed.optString(KEY_QUERY))) {
                    params.put(KEY_QUERY, constructed.optString(KEY_QUERY));
                }
            }

            params.put(KEY_NODE_TYPE, constructed.optString(KEY_NODE_TYPE, JcrConstants.NT_UNSTRUCTURED));

            params.put(KEY_SEARCH_PATH, constructed.getString(KEY_SEARCH_PATH));

            params.put(KEY_PROPERTIES_OPERAND, constructed.optString(KEY_PROPERTIES_OPERAND, "OR"));

            JSONArray properties = constructed.optJSONArray(KEY_PROPERTIES);
            Map<String, Object> map = new ValueMapDecorator(new HashMap<String, Object>());

            for (int i = 0; i < properties.length(); i++) {
                final String name = properties.getJSONObject(i).optString("name");
                final String value = properties.getJSONObject(i).optString("value");

                if (StringUtils.isNotBlank(name)) {
                    map.put(name, value);
                }
            }

            params.put(KEY_PROPERTIES, map);

        } else {

            // Raw query

            JSONObject raw = jsonParams.getJSONObject("raw");

            params.put(KEY_QUERY, raw.optString(KEY_QUERY));
            params.put(KEY_RELATIVE_PATH, StringUtils.stripToNull(raw.optString(KEY_RELATIVE_PATH)));
        }

        return params;
    }

    /**
     * Checks if the resource's properties are modifiable by the associated authenticated session
     *
     * @param resource the resource to modify
     * @return true if the current session can modify the resource's properties
     */
    protected final boolean canModifyProperties(Resource resource) {
        final Node node = resource.adaptTo(Node.class);
        try {
            if(!node.isCheckedOut()) {
                log.info("Resource [ {} ] is checked in, and cannot be modified", resource.getPath());
                return false;
            }
        } catch (RepositoryException e) {
            log.error("Repository error while checking checked out state", e);
            return false;
        }

        final Session session = resource.getResourceResolver().adaptTo(Session.class);
        final AccessControlManager accessControlManager;
        try {
            accessControlManager = session.getAccessControlManager();

            final Privilege modifyPropertiesPriviledge = accessControlManager.privilegeFromName(
                    Privilege.JCR_MODIFY_PROPERTIES);

            return accessControlManager.hasPrivileges(resource.getPath(), new Privilege[]{modifyPropertiesPriviledge});

        } catch (RepositoryException e) {
            log.error("Repository error occurred while checking access permissions on [ {} ].",
                    resource.getPath(), e);
        }

        return false;
    }
}