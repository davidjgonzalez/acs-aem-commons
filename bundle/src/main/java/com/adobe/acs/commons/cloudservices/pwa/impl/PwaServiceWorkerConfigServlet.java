package com.adobe.acs.commons.cloudservices.pwa.impl;

import com.adobe.acs.commons.cloudservices.pwa.Configuration;
import com.day.cq.commons.PathInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.models.factory.ModelFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import static com.adobe.acs.commons.cloudservices.pwa.impl.Constants.*;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.resourceTypes=cq:Page",
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.selectors=pwa.service-worker",
                "sling.servlet.extensions=json"
        }
)
public class PwaServiceWorkerConfigServlet extends SlingSafeMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(PwaServiceWorkerConfigServlet.class);

    private static final JsonElement NO_CACHE_CSRF;

    static {
        NO_CACHE_CSRF = new JsonPrimitive("(.*)/libs/granite/csrf/token.json(\\?.*)?");
    }

    @Reference
    private ModelFactory modelFactory;

    @Override
    protected final void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(getConfig(request).toString());
    }

    private JsonObject getConfig(SlingHttpServletRequest request) {
        final Configuration configuration = modelFactory.createModel(request, Configuration.class);
        final ValueMap properties = configuration.getProperties();

        final JsonObject json = new JsonObject();

        final int version = properties.get(PN_VERSION, 1);

        json.addProperty(KEY_CACHE_NAME,
                "aem-pwa__"
                        + properties.get(PN_SHORT_NAME, configuration.getConfPage().getName())
                        + "-v" + String.valueOf(version));

        json.addProperty(KEY_VERSION, version);
        json.addProperty(KEY_LAST_MODIFIED, configuration.getLastModified().getTimeInMillis());

        json.add(KEY_FALLBACK, getFallback(request, configuration));
        json.add(KEY_NO_CACHE, getNoCache(configuration));
        json.add(KEY_PRE_CACHE, getPreCache(request, configuration));

        return json;
    }

    private JsonArray getFallback(final SlingHttpServletRequest request, final Configuration configuration) {
        final JsonArray jsons = new JsonArray();
        final Resource resource = configuration.getConfPage().getContentResource(NN_FALLBACK);

        if (resource != null) {
            StreamSupport.stream(resource.getChildren().spliterator(), false)
                    .map(Resource::getValueMap)
                    .filter(p -> p.get(PN_VALUE, String.class) != null)
                    .filter(p -> p.get(PN_FALLBACK_PATH, String.class) != null)
                    .forEach(p -> {
                        final JsonObject json = new JsonObject();

                        json.addProperty(KEY_PATTERN, getPattern(p));
                        json.addProperty(KEY_PATH,
                                request.getResourceResolver().map(request,
                                        addExtension(p.get(PN_FALLBACK_PATH, String.class))));

                        jsons.add(json);
                    });

            /** Default fallback **/
            final String defaultFallbackPath = resource.getValueMap().get(PN_FALLBACK_PATH, String.class);

            if (StringUtils.isNotBlank(defaultFallbackPath)) {
                final JsonObject json = new JsonObject();
                json.addProperty(KEY_PATTERN, ".*");
                json.addProperty(KEY_PATH, request.getResourceResolver().map(request, addExtension(defaultFallbackPath)));
                jsons.add(json);
            }
        }
        return jsons;
    }

    private JsonArray getNoCache(final Configuration configuration) {
        final JsonArray jsons = new JsonArray();
        final Resource resource = configuration.getConfPage().getContentResource(NN_NO_CACHE);

        if (resource != null) {
            StreamSupport.stream(resource.getChildren().spliterator(), false)
                    .map(Resource::getValueMap)
                    .filter(p -> p.get(PN_VALUE, String.class) != null)
                    .forEach(p -> {
                        jsons.add(new JsonPrimitive(getPattern(p)));
                    });

        }

        jsons.add(NO_CACHE_CSRF);

        return jsons;
    }

    private JsonArray getPreCache(final SlingHttpServletRequest request, final Configuration configuration) {
        final JsonArray jsons = new JsonArray();
        final Resource resource = configuration.getConfPage().getContentResource(NN_PRE_CACHE);

        if (resource != null) {
            StreamSupport.stream(resource.getChildren().spliterator(), false)
                    .map(Resource::getValueMap)
                    .filter(p -> p.get(PN_VALUE, String.class) != null)
                    .forEach(p -> {
                        jsons.add(
                                new JsonPrimitive(
                                        request.getResourceResolver().map(request,
                                                addExtension(p.get(PN_VALUE, String.class)))));
                    });

        }
        return jsons;
    }

    private String getPattern(ValueMap properties) {
        final String type = properties.get(PN_TYPE, String.class);
        final String value = properties.get(PN_VALUE, String.class);
        final Pattern pattern;

        if (VALUE_PATTERN_CONTAINS.equals(type)) {
            pattern = Pattern.compile(".*(" + value + ").*");
        } else if (VALUE_PATTERN_STARTS_WITH.equals(type)) {
            pattern = Pattern.compile("^(" + value + ").*");
        } else if (VALUE_PATTERN_ENDS_WITH.equals(type)) {
            pattern = Pattern.compile(".*(" + value + ")$");
        } else {
            pattern = Pattern.compile(value);
        }

        return pattern.toString();
    }

    private String addExtension(String value) {
        final PathInfo pathInfo = new PathInfo(value);

        if (StringUtils.isBlank(pathInfo.getExtension())) {
            value += HTML_EXTENSION;
        }

        return value;
    }
}