package com.adobe.acs.commons.errors.errorhandler.impl;

import com.adobe.acs.commons.errors.errorhandler.ErrorHandler;
import com.adobe.acs.commons.errors.errorhandler.ErrorResponse;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ImageErrorHandlerImpl extends AbstractErrorHandler implements ErrorHandler {
    final Logger log = LoggerFactory.getLogger(ImageErrorHandlerImpl.class);

    private boolean enabled = true;

    private String errorImagePath;

    private String[] errorImageExtensions = new String[]{ "png", "jpg", "jpeg", "gif"};

    @Reference
    private ErrorHandler pageErroHandler;

    @Override
    public boolean accepts(final SlingHttpServletRequest request, Resource errorResource) {
        if (!enabled) {
            return false;
        } else if (StringUtils.isBlank(errorImagePath)) {
            log.warn("ACS AEM Commons error page handler enabled to handle error images, "
                    + "but no error image path was provided.");
            return false;
        }

        // Get the extension from the HTTP Request
        final String extension = StringUtils.stripToEmpty(StringUtils.lowerCase(
                request.getRequestPathInfo().getExtension()));

        return ArrayUtils.contains(errorImageExtensions, extension);
    }

    @Override
    public ErrorResponse getPath(final SlingHttpServletRequest request, Resource errorResource) {
        String errorPagePath = pageErroHandler.getPath(request, errorResource);

        if (StringUtils.startsWith(this.errorImagePath, "/")) {
            // Absolute path
            return this.errorImagePath;
        } else if (StringUtils.isNotBlank(errorPagePath)) {
            // Selector or Relative path; compute path based off found error page

            if (StringUtils.startsWith(this.errorImagePath, ".")) {
                final String selectorErrorImagePath = errorPagePath + this.errorImagePath;
                log.debug("Using selector-based error image: {}", selectorErrorImagePath);
                return selectorErrorImagePath;
            } else {
                final String relativeErrorImagePath = errorPagePath + "/"
                        + StringUtils.removeStart(this.errorImagePath, "/");
                log.debug("Using relative path-based error image: {}", relativeErrorImagePath);
                return relativeErrorImagePath;
            }
        } else {
            log.warn("Error image path found, but no error page could be found so relative path cannot "
                    + "be applied: {}", this.errorImagePath);
        }


    }
}
