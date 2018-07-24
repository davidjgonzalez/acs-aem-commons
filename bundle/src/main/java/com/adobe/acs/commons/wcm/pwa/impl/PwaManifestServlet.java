package com.adobe.acs.commons.wcm.pwa.impl;

import com.adobe.granite.confmgr.Conf;
import com.adobe.granite.confmgr.ConfMgr;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.resourceTypes=cq:Page",
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.selectors=pwa",
                "sling.servlet.selectors=manifest",
                "sling.servlet.extensions=load",
                "sling.servlet.extensions=js"
        }
)
public class PwaManifestServlet extends SlingSafeMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(PwaManifestServlet.class);
    @Reference
    ConfMgr confMgr;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    protected final void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(handleManifest(request));
    }

    /**
     * Example: HTTP GET http://localhost:4502/content/we-retail/us/en.pwa.load/manifest.json
     * <p>
     * End-point for loading the PWA manifest.json in the context providing scope.
     * <p>
     * This would load a JSON file available internally at some path specified at:
     * <p>
     * [cq:Page]/jcr:content/pwa/manifestPath = /apps/some/path/in/jcr/manifest.json
     * <p>
     * This could let different PWA's on an AEM instance have different manifest implementations.
     * <p>
     * TODO: Ideally this JSON could be built from edittable page properties instead of a file.
     *
     * @param request
     * @return
     */
    private String handleManifest(SlingHttpServletRequest request) throws ServletException {
        final JsonObject jsonObject = new JsonObject();

        final ValueMap manifestSettings = getConfigProperties(request);

        if (manifestSettings != null) {
            jsonObject.addProperty("name", manifestSettings.get("applicationName", "PWAName"));
            jsonObject.addProperty("short_name", manifestSettings.get("shortName", "PWA ShortName"));
            //jsonObject.addProperty("icons", getManifestIcons(manifestSettings));
            jsonObject.addProperty("start_url", manifestSettings.get("startUrl", "."));
            jsonObject.addProperty("background_color", manifestSettings.get("bgColor", "#FFFFFF"));
            jsonObject.addProperty("display", manifestSettings.get("display", "standalone"));
            jsonObject.addProperty("scope", manifestSettings.get("scope", ""));
            jsonObject.addProperty("theme_color", manifestSettings.get("themeColor", "#000000"));
        } else {
            throw new ServletException("No PWA manifest configuration found");
        }

        return jsonObject.toString();
    }

    private JsonArray getManifestIcons(ValueMap manifestSettings) {
        String[] iconImages = manifestSettings.get("src", new String[]{});
        String[] iconSizes = manifestSettings.get("size", new String[]{});
        JsonArray jsonArray = new JsonArray();

        if (iconImages.length == iconSizes.length) {
            for (int i = 0; i < iconImages.length; i++) {
                final JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("src", iconImages[i]);
                jsonObject.addProperty("type", "image/png");
                jsonObject.addProperty("sizes", iconSizes[i]);

                jsonArray.add(jsonObject);
            }
        }

        return jsonArray;
    }

    private ValueMap getConfigProperties(SlingHttpServletRequest request) {
        ResourceResolver serviceResolver = getServiceResolver();
        PageManager pageManager = serviceResolver.adaptTo(PageManager.class);
        // PageManager pageManager = request.getResourceResolver().adaptTo(PageManager.class);
        Page page = pageManager.getContainingPage(request.getResource());
        Conf conf = confMgr.getConf(page.adaptTo(Resource.class), serviceResolver);
        return conf.getItem("cloudconfigs/pwa/pwa-configuration");

        // TODO: Close Service Resolver

    }

    private ResourceResolver getServiceResolver() {
        final Map<String, Object> authInfo =
                Collections.singletonMap(ResourceResolverFactory.SUBSERVICE,
                        "pwa-service-handler");
        ResourceResolver resourceResolver = null;
        try {
            resourceResolver = resolverFactory.getServiceResourceResolver(authInfo);
        } catch (Exception e) {
            log.debug(e.getMessage());
        }

        return resourceResolver;
    }

}