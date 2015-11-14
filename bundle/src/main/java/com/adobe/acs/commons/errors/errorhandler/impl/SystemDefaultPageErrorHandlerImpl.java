package com.adobe.acs.commons.errors.errorhandler.impl;

import com.adobe.acs.commons.errors.errorhandler.ErrorHandler;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component
@Service
public class SystemDefaultPageErrorHandlerImpl extends AbstractErrorHandler implements ErrorHandler {
    final Logger log = LoggerFactory.getLogger(ImageErrorHandlerImpl.class);

    private String path = null;

    private String extension = "html";

    @Override
    public boolean accepts(final SlingHttpServletRequest request, Resource errorResource) {
        return (StringUtils.isNotBlank(this.path));
    }

    @Override
    public String getPath(final SlingHttpServletRequest request, Resource errorResource) {
        return this.path;
    }

    @Activate
    protected void activate(Map<String, Object> config) {
        this.path = StringUtils.trim(this.path);

        if (StringUtils.isNotBlank(this.extension)) {
            this.path += "." + this.extension;
        }
    }
}
