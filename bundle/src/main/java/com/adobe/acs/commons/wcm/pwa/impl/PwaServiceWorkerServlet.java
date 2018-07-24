package com.adobe.acs.commons.wcm.pwa.impl;

import com.adobe.granite.confmgr.Conf;
import com.adobe.granite.confmgr.ConfMgr;
import com.adobe.granite.ui.clientlibs.ClientLibrary;
import com.adobe.granite.ui.clientlibs.HtmlLibrary;
import com.adobe.granite.ui.clientlibs.HtmlLibraryManager;
import com.adobe.granite.ui.clientlibs.LibraryType;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.OptingServlet;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Component(
        immediate = true,
        service = Servlet.class,
        property = {
                "sling.servlet.resourceTypes=cq:Page",
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.selectors=pwa",
                "sling.servlet.selectors=service-worker",
                "sling.servlet.extensions=load",
                "sling.servlet.extensions=js",
        }
)
public class PwaServiceWorkerServlet extends SlingSafeMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(PwaServiceWorkerServlet.class);

    @Reference
    private HtmlLibraryManager htmlLibraryManager;

    @Override
    protected final void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
            ServletException, IOException {

        response.setContentType("application/javascript");
        response.setHeader("Content-Disposition", "attachment");
        response.setCharacterEncoding("UTF-8");

        Collection<ClientLibrary> htmlLibraries =
                htmlLibraryManager.getLibraries(new String[] { "acs-commons.pwa.service-worker" },
                        LibraryType.JS, true, false);

        if (htmlLibraries.size() > 0) {
            htmlLibraries.stream()
                    .map(hl -> htmlLibraryManager.getLibrary(LibraryType.JS, hl.getPath()))
                    .filter(Objects::nonNull)
                    .forEach(library -> {
                        try {
                            response.getWriter().write(IOUtils.toString(library.getInputStream()));
                            response.flushBuffer();
                        } catch (IOException e) {
                            log.error("Error streaming JS Client Library at [ {} ] to response", library.getPath());
                        }
                    });

        } else {
            response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
        }
    }
}