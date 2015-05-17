package com.adobe.acs.commons.workflow.audit;

import com.adobe.acs.commons.util.TextUtil;
import com.adobe.acs.commons.workflow.audit.impl.Constants;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.commons.util.DamUtil;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WorkflowAuditUtil {

    public static List<ValueMap> getWorkItems(final Resource workflowAuditResource) {

        final ResourceResolver resourceResolver = workflowAuditResource.getResourceResolver();

        List<ValueMap> workItems = new ArrayList<ValueMap>();

        final Iterator<Resource> resources = workflowAuditResource.listChildren();

        while (resources.hasNext()) {
            final Resource resource = resources.next();
            final ValueMap properties = resource.adaptTo(ValueMap.class);

            if (properties.get(Constants.PN_IS_CONTAINER, false)) {
                // Is a container; get that AuditResource
                final String nextPath = properties.get(Constants.PN_CONTAINEE_AUDIT_PATH, String.class);

                if (StringUtils.isNotBlank(nextPath)) {
                    workItems.addAll(getWorkItems(resourceResolver.getResource(nextPath)));
                }
            } else {
                // Normal workItem
                workItems.add(properties);
            }
        }

        return workItems;
    }


    public static String getPayloadTitle(final ResourceResolver resourceResolver, final String payload) {
        final Resource resource = resourceResolver.getResource(payload);

        final Asset asset = DamUtil.resolveToAsset(resource);

        if(asset != null) {
            return  TextUtil.getFirstNonEmpty(asset.getMetadataValue("dc:title"), asset.getName());
        }

        final Page page = resourceResolver.adaptTo(PageManager.class).getContainingPage(resource);

        if(page != null) {
            return TextUtil.getFirstNonEmpty(page.getTitle(), page.getPageTitle(), page.getNavigationTitle(), page
                    .getName());
        }

        final ValueMap properties = resource.adaptTo(ValueMap.class);

        if(properties != null) {
            return properties.get(JcrConstants.JCR_TITLE, String.class);
        }

        return null;
    }

    public static String getPayloadContent(final ResourceResolver resourceResolver, final String payload) {
        final Resource resource = resourceResolver.getResource(payload);

        final Asset asset = DamUtil.resolveToAsset(resource);

        if(asset != null) {
            return  asset.getPath();
        }

        final Page page = resourceResolver.adaptTo(PageManager.class).getContainingPage(resource);

        if(page != null) {
            return page.getPath();
        }

        return resource.getPath();
    }
}
