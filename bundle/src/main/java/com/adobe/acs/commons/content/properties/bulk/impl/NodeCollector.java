package com.adobe.acs.commons.content.properties.bulk.impl;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import org.apache.commons.collections.iterators.EmptyIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NodeCollector {

    public static final Iterator<Node> getNodesFromRawQuery(final ResourceResolver resourceResolver,
                                                            final String expression) throws RepositoryException {

        final Session session = resourceResolver.adaptTo(Session.class);
        final QueryManager queryManager = session.getWorkspace().getQueryManager();
        final javax.jcr.query.Query query = queryManager.createQuery(expression, "JCR-SQL2");
        final QueryResult result = query.execute();

        return result.getNodes();
    }

    public static final Iterator<Node> getNodesFromConstructedQuery(final ResourceResolver resourceResolver,
                                                                    final String path, final String nodeType,
                                                                    final ValueMap properties) {

        final QueryBuilder queryBuilder = resourceResolver.adaptTo(QueryBuilder.class);
        final Map<String, String> map = new HashMap<String, String>();

        map.put("path", path);
        map.put("type", nodeType);

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


    public static final Iterator<Node> getNodesFromConstructedTraversal(final ResourceResolver resourceResolver,
                                                                        final String path,
                                                                        final String nodeType,
                                                                        final String operandStr,
                                                                        final ValueMap properties) {
        final Resource root = resourceResolver.getResource(path);

        if (root != null) {
            final TraversalInclusionPolicy.Operand operand = TraversalInclusionPolicy.Operand.valueOf(
                    StringUtils.upperCase(operandStr));

            return TreeTraverser.nodeIterator(root.adaptTo(Node.class), TreeTraverser.ErrorHandler.IGNORE,
                    new TraversalInclusionPolicy(resourceResolver, nodeType, operand, properties));
        } else {
            return EmptyIterator.INSTANCE;
        }
    }
}
