package com.adobe.acs.commons.contentservices.impl;


import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;

/**
 * SlingHttpServletRequest Wrapper that allows the force setting (or removal) of the original requests extension.
 *
 * This is useful when doing internal request dispatcher (include or forward) as there is no way to reset the extension via RequestDispatcherOptions.
 */
public class ContentServicesOverrideRequestWrapper extends SlingHttpServletRequestWrapper {
    private final String extension;

    /**
     * @param wrappedRequest the request to wrap;
     * @param extension the extension to force. Set to null for no extension;
     */
    public ContentServicesOverrideRequestWrapper(SlingHttpServletRequest wrappedRequest, String extension) {
        super(wrappedRequest);
        this.extension = extension;
    }

    @Override
    public RequestPathInfo getRequestPathInfo() {
        return new RequestPathInfoWrapper(super.getRequestPathInfo());
    }

    private class RequestPathInfoWrapper implements RequestPathInfo {
        private final RequestPathInfo requestPathInfo;

        public RequestPathInfoWrapper(RequestPathInfo requestPathInfo) {
            this.requestPathInfo = requestPathInfo;
        }

        public String getResourcePath() {
            return requestPathInfo.getResourcePath();
        }

        public String getExtension() {
            return "json";
        }

        public String getSelectorString() {
            return "model.tidy";
        }

        public String[] getSelectors() {
            return new String[]{ "model", "tidy" };
        }

        public String getSuffix() {
            return requestPathInfo.getSuffix();
        }

        public Resource getSuffixResource() {
            return requestPathInfo.getSuffixResource();
        }
    }
}