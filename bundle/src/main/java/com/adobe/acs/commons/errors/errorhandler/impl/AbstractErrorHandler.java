package com.adobe.acs.commons.errors.errorhandler.impl;

import com.adobe.acs.commons.errorpagehandler.ErrorPageHandlerService;
import com.day.cq.commons.PathInfo;
import com.day.cq.commons.inherit.HierarchyNodeInheritanceValueMap;
import com.day.cq.commons.inherit.InheritanceValueMap;
import com.day.cq.commons.jcr.JcrConstants;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public abstract class AbstractErrorHandler {
    final Logger log = LoggerFactory.getLogger(AbstractErrorHandler.class);

    @Override
    public String getErrorsPath(final SlingHttpServletRequest request, Resource errorResource) {
        final Resource realResource = this.findFirstRealParentOrSelf(request, errorResource);

        if(realResource == null) {
            log.debug("Could not locate a real resource");
            return null;
        }

        // Get content resource of the page
        final Resource contentResource = realResource.getChild(JcrConstants.JCR_CONTENT);

        if (contentResource == null) {
            return null;
        }

        final InheritanceValueMap pageProperties = new HierarchyNodeInheritanceValueMap(contentResource);
        String path = pageProperties.getInherited(ERROR_PAGE_PROPERTY, String.class);

        // could not find inherited property
        if (StringUtils.isBlank(path)) {
            for (final Map.Entry<String, String> entry : osgiPathMap.entrySet()) {
                if (StringUtils.startsWith(errorResource.getPath(), entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        // Return null of nothing can be found anywhere
        return StringUtils.defaultIfEmpty(path, null);
    }



    /**
     * Given the Request path, find the first Real Parent of the Request (even if the resource doesnt exist).
     *
     * @param request the request object
     * @param errorResource the error resource
     * @return
     */
    protected Resource findFirstRealParentOrSelf(SlingHttpServletRequest request, Resource errorResource) {
        if (errorResource == null) {
            return null;
        }

        final ResourceResolver resourceResolver = errorResource.getResourceResolver();

        // Get the lowest aggregate node ancestor for the errorResource
        String path = StringUtils.substringBefore(errorResource.getPath(), JcrConstants.JCR_CONTENT);

        Resource resource = errorResource;

        if (!StringUtils.equals(path, errorResource.getPath())) {
            // Only resolve the resource if the path of the errorResource is different from the cleaned up path; else
            // we know the errorResource and what the path resolves to is the same
            resource = resourceResolver.resolve(request, path);
        }

        // If the resource exists, then use it!
        if (!ResourceUtil.isNonExistingResource(resource)) {
            return resource;
        }

        // Quick check for the Parent; Handles common case of deactivated pages
        final Resource parent = resource.getParent();
        if (parent != null) {
            log.debug("Found real aggregate resource via getParent() at [ {} ]", parent.getPath());
            return parent;
        }

        // Start checking the path until the first real ancestor is found
        final PathInfo pathInfo = new PathInfo(resource.getPath());
        String[] parts = StringUtils.split(pathInfo.getResourcePath(), '/');

        for (int i = parts.length - 1; i >= 0; i--) {
            String[] tmpArray = (String[]) ArrayUtils.subarray(parts, 0, i);
            String candidatePath = "/".concat(StringUtils.join(tmpArray, '/'));

            final Resource candidateResource = resourceResolver.resolve(request, candidatePath);

            if (candidateResource != null && !ResourceUtil.isNonExistingResource(candidateResource)) {
                return candidateResource;
            }
        }

        return null;
    }

    protected int getStatusCode(SlingHttpServletRequest request) {
        final Integer statusCode = (Integer) request.getAttribute(SlingConstants.ERROR_STATUS);
        return statusCode != null ? statusCode : ErrorPageHandlerService.DEFAULT_STATUS_CODE;
    }

}
