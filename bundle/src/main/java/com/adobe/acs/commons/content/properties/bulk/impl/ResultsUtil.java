package com.adobe.acs.commons.content.properties.bulk.impl;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.Resource;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.io.InputStream;

public class ResultsUtil {

    public static String storeResults(Resource resource, InputStream inputstream) throws RepositoryException {
        Node node = resource.adaptTo(Node.class);

        Node parent;
        if(node.hasNode("results")) {
            parent = node.getNode("results");
        } else {
            parent = node.addNode("results", "sling:OrderedFolder");
        }

        Node result = JcrUtils.putFile(parent,
                String.valueOf(System.currentTimeMillis()) + ".csv",
                "text/csv",
                inputstream);

        result.getSession().save();

        return result.getPath();
    }

}
