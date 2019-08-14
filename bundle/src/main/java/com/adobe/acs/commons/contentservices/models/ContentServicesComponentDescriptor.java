package com.adobe.acs.commons.contentservices.models;

import com.adobe.acs.commons.contentservices.ContentServicesComponentFilter;
import com.adobe.acs.commons.contentservices.impl.ContentServicesOverrideRequestWrapper;
import com.adobe.acs.commons.util.BufferedSlingHttpServletResponse;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Required;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;

@Model(
        adaptables = {SlingHttpServletRequest.class},
        adapters = {ContentServicesComponentDescriptor.class},
        resourceType = {"acs-commons/content-services/component-descriptor"}
)
public class ContentServicesComponentDescriptor {
    private static final Logger log = LoggerFactory.getLogger(ContentServicesComponentDescriptor.class);

    @Self
    @Required
    private SlingHttpServletRequest request;


    public String getJsonKey() {
        return request.getResource().getValueMap().get("sling:alias", request.getResource().getName());
    }

    public String getJsonPath() {
        return request.getResource().getPath();
    }

    public String getJsonUrl() {
        /*
        final StringWriter sw = new StringWriter();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final SlingHttpServletRequest wrappedRequest = new ContentServicesOverrideRequestWrapper(request, "json");
        final SlingHttpServletResponse wrappedResponse = new BufferedSlingHttpServletResponse((SlingHttpServletResponse) request.getAttribute(ContentServicesComponentFilter.RESPONSE_KEY), sw, baos);

        final RequestDispatcherOptions options = new RequestDispatcherOptions();
        options.setReplaceSelectors("model.tidy");

        final RequestDispatcher requestDispatcher = request.getRequestDispatcher(request.getResource(), options);

        try {
            requestDispatcher.include(wrappedRequest, wrappedResponse);
            return sw.toString();
        } catch (ServletException | IOException e) {
            log.error("Unable to generate JSON", e);
        }

        return "Unable to generate JSON";
         */
        return  request.getResource().getPath() + ".model.json";
    }
}
