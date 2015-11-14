package com.adobe.acs.commons.errors.errorhandler.impl;

import com.adobe.acs.commons.errors.errorhandler.ErrorHandler;
import com.adobe.acs.commons.errors.errorhandler.ErrorResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;

@Component
@Service
public class PageErrorHandlerImpl extends AbstractErrorHandler implements ErrorHandler {
    final Logger log = LoggerFactory.getLogger(ImageErrorHandlerImpl.class);

    private String extension = "html";

    @Override
    public boolean accepts(final SlingHttpServletRequest request, Resource errorResource) {
        return false;
    }

    @Override
    public ErrorResponse getErrorReponse(final SlingHttpServletRequest request, Resource errorResource) {
        String path = null;
        final String errorsPath = this.getErrorsPath(request, errorResource);

        if (StringUtils.isBlank(errorsPath)) {
            // Pass onto next handler
            return null;
        }

        // Find error page by Status Code name
        final String errorPath = errorsPath + "/" + this.getStatusCode(request);
        final Resource errorPageResource = request.getResourceResolver().resolve(request, errorPath);

        if (errorPageResource != null) {
            if (StringUtils.isNotBlank(extension)) {
                path = errorPageResource.getPath() + "." + extension;
            } else {
                path = errorPageResource.getPath();
            }

            return new PageErrorResponse(this.getStatusCode(request), path);
        } else {
            return null;
        }
    }

    /**
     * Gets the Error Pages Path for the provided content root path.
     *
     * @param rootPath
     * @param errorPagesMap
     * @return
     */
    public String getErrorPagesPath(String rootPath, Map<String, String> errorPagesMap) {
        if (errorPagesMap.containsKey(rootPath)) {
            return errorPagesMap.get(rootPath);
        } else {
            return null;
        }
    }


    /**
     * Page Error Handler Response
     */
    private class PageErrorResponse implements ErrorResponse {
        private final String path;
        private final int statusCode;

        public PageErrorResponse(int statusCode, String path) {
            this.statusCode = statusCode;
            this.path = path;
        }

        @Override
        public void updateResponse(SlingHttpServletRequest request, SlingHttpServletResponse response) {
            if (response.isCommitted()) {
                log.warn("Erring response already committed. There is nothing we can do.");
                return;
            }

            // Clear client libraries

            // Replace with proper API call is HtmlLibraryManager provides one in the future;
            // Currently this is our only option.
            request.setAttribute(com.day.cq.widget.HtmlLibraryManager.class.getName() + ".included",
                    new HashSet<String>());
            // Clear the response
            response.reset();
            response.setContentType("text/html");
            response.setStatus(statusCode);
        }

        @Override
        public String getPath() {
            return this.path;
        }

        @Override
        public boolean isCacheable() {
            return true;
        }
    }
}
