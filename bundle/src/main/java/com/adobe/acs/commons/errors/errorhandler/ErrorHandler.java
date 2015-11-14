package com.adobe.acs.commons.errors.errorhandler;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;

/**
 * Created by david on 11/11/15.
 */
public interface ErrorHandler {

    boolean accepts(SlingHttpServletRequest request, Resource errorResource);

    ErrorResponse getErrorReponse(SlingHttpServletRequest request, Resource errorResource);

}
