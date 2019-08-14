package com.adobe.acs.commons.contentservices;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.osgi.service.component.annotations.Component;

import javax.servlet.*;
import java.io.IOException;

@Component(
        property = {
                "sling.filter.scope=INCLUDE",
                "sling.filter.pattern=/content/.*",
                "sling.filter.methods=GET",
                "sling.filter.extensions=html"

        }
)
public class ContentServicesComponentFilter  implements Filter {

    private static final String  RECURSION_KEY = "acs-aem-commons__content-services-filter--recursion-key";


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        final SlingHttpServletRequest request = (SlingHttpServletRequest) servletRequest;
        final SlingHttpServletResponse response = (SlingHttpServletResponse)servletResponse;

        if (!accepts(request)) {
            chain.doFilter(request, response);
            return;
        }

        // Else is a candidate so process

        final RequestDispatcherOptions options  = new RequestDispatcherOptions();
        options.setForceResourceType("acs-commons/content-services/component-description");

        final RequestDispatcher requestDispatcher = request.getRequestDispatcher(request.getResource(), options);

        response.getWriter().write("<div class=\"aem-content-services\">");

        request.setAttribute(RECURSION_KEY, Boolean.TRUE);

        response.getWriter().write("<div class=\"aem-content-services__descriptor\">");
        requestDispatcher.include(request, response);
        response.getWriter().write("</div>");

        request.setAttribute(RECURSION_KEY, null);

        response.getWriter().write("<div class=\"aem-content-services__component\">");
        chain.doFilter(request, response);
        response.getWriter().write("</div>");

        response.getWriter().write("</div>");
    }

    @Override
    public void destroy() {

    }

    private boolean accepts(final SlingHttpServletRequest request) {
        if (request.getAttribute(RECURSION_KEY) != null) {
            return false;
        } else if (!StringUtils.contains(request.getResource().getPath(), "/root")) {
            return false;
        } else if ("jcr:content".equals(request.getResource().getName())) {
            return false;
        } else if (request.getResourceResolver().isResourceType(request.getResource(), "cq/Page")) {
            return false;
        }

        PageManager pageManager = request.getResourceResolver().adaptTo(PageManager.class);
        Page page = pageManager.getContainingPage(request.getResource());

        if (page == null) {
            return false;
        }

        // Do some more checks

        return true;

    }
}