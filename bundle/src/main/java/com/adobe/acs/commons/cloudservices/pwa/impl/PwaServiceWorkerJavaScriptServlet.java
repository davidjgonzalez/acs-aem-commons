package com.adobe.acs.commons.cloudservices.pwa.impl;

import com.adobe.acs.commons.cloudservices.pwa.Configuration;
import com.adobe.granite.ui.clientlibs.ClientLibrary;
import com.adobe.granite.ui.clientlibs.HtmlLibraryManager;
import com.adobe.granite.ui.clientlibs.LibraryType;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.models.factory.ModelFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.resourceTypes=cq:Page",
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.selectors=pwa.service-worker",
                "sling.servlet.extensions=js",
        }
)
public class PwaServiceWorkerJavaScriptServlet extends SlingSafeMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(PwaServiceWorkerJavaScriptServlet.class);

    @Reference
    private HtmlLibraryManager htmlLibraryManager;

    @Reference
    private ModelFactory modelFactory;

    @Override
    protected final void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {

        response.setContentType("application/javascript");
        response.setHeader("Content-Disposition", "attachment");
        response.setCharacterEncoding("UTF-8");

        writeJavaScript(request, response);
    }

    private void writeJavaScript(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        final Configuration configuration = modelFactory.createModel(request, Configuration.class);

        final Collection<ClientLibrary> htmlLibraries =
                htmlLibraryManager.getLibraries(configuration.getServiceWorkerJsCategories(),
                        LibraryType.JS, true, false);

        if (htmlLibraries.size() > 0) {
            /** This will force the Service Worker to re-load when the Conf has changed **/
            // TODO better way to do this? Previously uses JS comment to break file hash, however that is removed during minification.
            response.getWriter().println("var acs_commons_pwa_config_last_modified = " + String.valueOf(configuration.getLastModified().getTimeInMillis()) + ";");

            htmlLibraries.stream()
                    .map(hl -> htmlLibraryManager.getLibrary(LibraryType.JS, hl.getPath()))
                    .filter(Objects::nonNull)
                    .forEach(library -> {
                        try {
                            response.getWriter().write(IOUtils.toString(library.getInputStream()));
                            response.flushBuffer();
                        } catch (IOException e) {
                            log.error("Error streaming JS Client Library at [ {} ] to response for PWA Service Worker JS request.", library.getPath());
                        }
                    });

        } else {
            response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
        }
    }
}