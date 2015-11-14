package com.adobe.acs.commons.errors.errorhandler;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;

public interface ErrorResponse {
    String getPath();
    boolean isCacheable();
    void updateResponse(SlingHttpServletRequest request, SlingHttpServletResponse response);
}
