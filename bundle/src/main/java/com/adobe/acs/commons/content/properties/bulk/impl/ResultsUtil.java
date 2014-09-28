package com.adobe.acs.commons.content.properties.bulk.impl;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class ResultsUtil {
    public static final String NN_RESULTS = "results";
    public static final String NN_DRY_RUN = "dry-run";

    public static Resource getResultResource(Resource resource, boolean dryRun) {
        PageManager pageManager = resource.getResourceResolver().adaptTo(PageManager.class);
        Page page = pageManager.getContainingPage(resource);

        Resource contentResource = page.getContentResource();

        if(dryRun) {
            return contentResource.getChild(NN_DRY_RUN);
        } else {
            return contentResource.getChild(NN_RESULTS);
        }
    }

    public static String createResults(List<Result> results,
                                       ValueMap params,
                                       Resource resource) throws RepositoryException {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(baos);

        int success = 0;
        int error = 0;
        int noop = 0;

        for(final Result result : results) {
            if (Result.Status.SUCCESS.equals(result.getStatus())) {
                success++;
            } else if (Result.Status.ERROR.equals(result.getStatus())) {
                error++;
            } else if (Result.Status.ACCESS_ERROR.equals(result.getStatus())) {
                error++;
            } else if (Result.Status.RELATIVE_PATH_NOT_FOUND.equals(result.getStatus())) {
                error++;
            } else if (Result.Status.NOOP.equals(result.getStatus())) {
                noop++;
            }

            // Print the CSV entry
            printStream.println(result.toString());
        }

        ValueMap resultProperties =
                new ValueMapDecorator(new HashMap<String, Object>());

        resultProperties.put("operation", params.get("operation", "unknown"));
        resultProperties.put("total", success + error + noop);
        resultProperties.put("success", success);
        resultProperties.put("error", error);
        resultProperties.put("noop", noop);

        final String nodeName = String.valueOf(System.currentTimeMillis());

        Node parent = resource.adaptTo(Node.class);
        parent = JcrUtils.getOrCreateUniqueByPath(parent, nodeName, JcrConstants.NT_UNSTRUCTURED);

        addResultProperties(resource.getResourceResolver(), resultProperties, parent);

        final Node file = JcrUtils.putFile(parent,
                nodeName + ".csv",
                "text/csv",
                new ByteArrayInputStream(baos.toByteArray()));

        file.getSession().save();

        return parent.getPath();
    }

    public static Collection<? extends Result> convertToStatus(final List<Result> results,
                                                               final Result.Status status) {
        final List<Result> convertedResults = new ArrayList<Result>();

        for(final Result result : results) {
            convertedResults.add(new Result(status, result.getPath()));
        }

        return convertedResults;
    }

    private static void addResultProperties(final ResourceResolver resourceResolver,
                                     final ValueMap properties,
                                     final Node parent) throws RepositoryException {
        Resource containerResource = resourceResolver.getResource(parent.getPath());
        ModifiableValueMap mvm = containerResource.adaptTo(ModifiableValueMap.class);
        mvm.putAll(properties);
    }


}
