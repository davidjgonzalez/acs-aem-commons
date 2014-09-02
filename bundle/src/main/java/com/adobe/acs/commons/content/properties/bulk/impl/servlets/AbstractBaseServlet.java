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


import com.adobe.acs.commons.content.properties.bulk.impl.Status;
import com.adobe.acs.commons.content.properties.bulk.impl.TraversalInclusionPolicy;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import com.day.jcr.vault.util.PathUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
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
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
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

    protected static final String VALUE_QUERY_MODE_CONSTRUCTED = "constructed";

    protected static final String VALUE_COLLECTION_MODE_TRAVERSAL = "traversal";

    protected static final String KEY_PROPERTIES_OPERAND = "propertiesOperand";

    private static final Logger log = LoggerFactory.getLogger(AbstractBaseServlet.class);

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

            if (dryRun) {
                response.setHeader("Content-Type", "text/csv");
            } else {
                response.setHeader("Content-Type", "application/json");
            }

            /* Process */

            final JSONObject jsonResponse = new JSONObject();

            final Session session = resourceResolver.adaptTo(Session.class);

            final List<String> successPaths = new ArrayList<String>();
            final List<String> noopPaths = new ArrayList<String>();
            final List<String> errorPaths = new ArrayList<String>();
            List<String> batchSuccessPaths = new ArrayList<String>();

            int total = 0;
            int count = 0;
            while (nodes.hasNext()) {
                total++;
                final Resource foundResource = resourceResolver.getResource(nodes.next().getPath());
                Resource resource = foundResource;

                // Relative path resolution

                if (StringUtils.isNotBlank(relativePath)) {
                    final Resource relativeResource = foundResource.getChild(relativePath);

                    if (relativeResource != null) {
                        resource = relativeResource;
                    } else {
                        if (dryRun) {
                            response.getWriter().println(Status.RELATIVE_PATH_NOT_FOUND.toString() + "," + resource.getPath());
                        } else {
                            errorPaths.add(PathUtil.makePath(foundResource.getPath(), relativePath));
                            log.warn("Could not find relative resource at [ {} ] ~> [ {} ]", foundResource.getPath(),
                                    relativePath);
                        }

                        // Could not find a relative resource; so skip and move on
                        continue;
                    }
                }

                // Resource processing

                if (dryRun) {
                    final Status status = execute(resource, params);
                    response.getWriter().println(status.toString() + "," + resource.getPath());
                } else {
                    final Status status = execute(resource, params);

                    if (Status.SUCCESS.equals(status)) {
                        // Success
                        batchSuccessPaths.add(resource.getPath());
                        count++;
                    } else if (Status.ERROR.equals(status)
                            || Status.ACCESS_ERROR.equals(status)) {
                        // Errors
                        errorPaths.add(resource.getPath());
                    } else if (Status.NOOP.equals(status)) {
                        // No-op
                        noopPaths.add(resource.getPath());
                    }

                    if (count != 0 && count % batchSize == 0) {
                        long start = System.currentTimeMillis();
                        session.save();
                        log.info("Saved batch of [ {} ] copy/move property changes in {} ms", batchSize,
                                System.currentTimeMillis() - start);

                        successPaths.addAll(batchSuccessPaths);
                        batchSuccessPaths = new ArrayList<String>();
                    }
                }
            }

            if (!dryRun && (count % batchSize != 0)) {
                long start = System.currentTimeMillis();
                session.save();
                log.info("Saved batch of [ {} ] copy/move property changes in {} ms", count % batchSize,
                        System.currentTimeMillis() - start);

                successPaths.addAll(batchSuccessPaths);
            }

            // Set to JSON
            if (!dryRun) {
                jsonResponse.put("total", total);
                jsonResponse.put("count", count);
                jsonResponse.put("successPaths", new JSONArray(successPaths));
                jsonResponse.put("errorPaths", new JSONArray(errorPaths));
                jsonResponse.put("noopPaths", new JSONArray(noopPaths));

                response.getWriter().print(jsonResponse.toString());
            }

        } catch (JSONException e) {
            log.error(e.getMessage());
            response.sendError(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error getting params");
        } catch (Exception e) {
            log.error(e.getMessage());
            response.sendError(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not process");
        }
    }

    private Iterator<Node> getNodes(final ResourceResolver resourceResolver, final ValueMap params, final String queryMode) throws RepositoryException {
        if (StringUtils.equals(queryMode, VALUE_QUERY_MODE_CONSTRUCTED)) {

            final String collectionMode = params.get(KEY_COLLECTION_MODE, String.class);

            if (StringUtils.equals(collectionMode, VALUE_COLLECTION_MODE_TRAVERSAL)) {
                log.trace("Executing constructed traversal");
                return this.getNodesFromConstructedTraversal(resourceResolver, params);
            } else {
                log.trace("Executing constructed query");
                return this.getNodesFromConstructedQuery(resourceResolver, params);
            }
        } else {
            log.trace("Executing raw query");
            return this.getNodesFromRawQuery(resourceResolver, params);
        }
    }


    protected ValueMap getParams(SlingHttpServletRequest request) throws JSONException {
        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());

        final JSONObject jsonParams = new JSONObject(request.getParameter(REQUEST_PARAM_PARAMS));

        params.put(KEY_DRY_RUN, jsonParams.optBoolean(KEY_DRY_RUN));
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

            if (StringUtils.isNotBlank(constructed.optString(KEY_NODE_TYPE))) {
                params.put(KEY_NODE_TYPE, constructed.optString(KEY_NODE_TYPE));
            }

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

    protected final boolean canModifyProperties(Resource resource) throws RepositoryException {
        final Session session = resource.getResourceResolver().adaptTo(Session.class);
        final AccessControlManager accessControlManager = session.getAccessControlManager();
        final Privilege modifyPropertiesPriviledge = accessControlManager.privilegeFromName(
                Privilege.JCR_MODIFY_PROPERTIES);

        return accessControlManager.hasPrivileges(resource.getPath(), new Privilege[]{ modifyPropertiesPriviledge });
    }


    protected final Iterator<Node> getNodesFromRawQuery(final ResourceResolver resourceResolver,
                                                        final ValueMap params) throws RepositoryException {

        final Session session = resourceResolver.adaptTo(Session.class);
        final QueryManager queryManager = session.getWorkspace().getQueryManager();
        final javax.jcr.query.Query query = queryManager.createQuery(params.get(KEY_QUERY, String.class), "");
        final QueryResult result = query.execute();

        return result.getNodes();
    }

    protected final Iterator<Node> getNodesFromConstructedQuery(final ResourceResolver resourceResolver,
                                                                final ValueMap params) {

        final QueryBuilder queryBuilder = resourceResolver.adaptTo(QueryBuilder.class);
        final Map<String, String> map = new HashMap<String, String>();

        map.put("path", params.get(KEY_SEARCH_PATH, ""));
        map.put("type", params.get(KEY_NODE_TYPE, ""));

        final ValueMap properties = params.get(KEY_PROPERTIES, ValueMap.EMPTY);

        if (properties != null) {

            int i = 1;
            for (final String key : properties.keySet()) {
                map.put(i + "_property", key);
                map.put(i + "_property.value", properties.get(key, String.class));
            }
        }

        map.put("p.limit", "-1");

        final Query query = queryBuilder.createQuery(PredicateGroup.create(map),
                resourceResolver.adaptTo(Session.class));
        final SearchResult queryResult = query.getResult();

        return queryResult.getNodes();
    }


    protected final Iterator<Node> getNodesFromConstructedTraversal(final ResourceResolver resourceResolver,
                                                                    final ValueMap params) {

        final Resource root = resourceResolver.getResource(params.get(KEY_SEARCH_PATH, String.class));
        final String nodeType = params.get(KEY_NODE_TYPE, String.class);
        final ValueMap properties = params.get(KEY_PROPERTIES, ValueMap.EMPTY);
        final TraversalInclusionPolicy.Operand operand = TraversalInclusionPolicy.Operand.valueOf(
                StringUtils.upperCase(params.get(KEY_PROPERTIES_OPERAND, "OR")));

        return TreeTraverser.nodeIterator(root.adaptTo(Node.class), TreeTraverser.ErrorHandler.IGNORE,
                new TraversalInclusionPolicy(resourceResolver, nodeType, operand, properties));
    }


    abstract Map<String, Object> getParams(JSONObject json) throws JSONException;

    abstract Status execute(Resource resource, ValueMap params);

}