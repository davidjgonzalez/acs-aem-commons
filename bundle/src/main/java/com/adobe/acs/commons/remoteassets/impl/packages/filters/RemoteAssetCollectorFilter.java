package com.adobe.acs.commons.remoteassets.impl.packages.filters;


import com.adobe.acs.commons.remoteassets.*;
import com.day.cq.dam.commons.util.DamUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Component
@Properties({
        @Property(name = "sling.filter.scope",
                  value = {"request"}),

        @Property(name = "service.ranking",
                  intValue = 200000)
})
@Service
public class RemoteAssetCollectorFilter implements Filter, RemoteAssetRequestCollector {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetCollectorFilter.class);

    @Reference
    private RemoteAssetsConfig config;

    @Reference
    private RemoteAssetsRenditionsSync remoteAssetsRenditionsSync;

    @Reference
    private RemoteAssetsRenditions remoteAssetsRenditions;


    private static final ThreadLocal<Map<String, Collection<String>>> THREAD_LOCAL = new ThreadLocal<Map<String, Collection<String>>>() {
        @Override
        protected Map<String, Collection<String>> initialValue() {
            return null;
        }
    };

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Usually, do nothing
    }

    @Override
    public final void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {

        if (accepts((SlingHttpServletRequest)request)) {
            THREAD_LOCAL.set(new ConcurrentHashMap<>());
        }

        try {
            chain.doFilter(request, response);

            if (isEnabled() && !get().isEmpty()) {
                ResourceResolver serviceResourceResolver = null;

                try {
                    serviceResourceResolver = config.getResourceResolver();

                    final ResourceResolver finalServiceResourceResolver = serviceResourceResolver;

                    get().keySet().stream()
                            .map(finalServiceResourceResolver::getResource)
                            .filter(Objects::nonNull)
                            .map(DamUtil::resolveToAsset)
                            .filter(Objects::nonNull)
                            .forEach(asset -> {
                                    remoteAssetsRenditions.addSyncingRendition(asset);
                            });

                    remoteAssetsRenditionsSync.syncAssetRenditions(serviceResourceResolver, get());

                } finally {
                    if (serviceResourceResolver != null) {

                        if (serviceResourceResolver.hasChanges()) {
                            //serviceResourceResolver.commit();
                        }
                        serviceResourceResolver.close();
                    }
                }
            }
        } finally {
            if (isEnabled()) {
                THREAD_LOCAL.remove();
            }
        }
    }

    private boolean accepts(final SlingHttpServletRequest request) {
        if (!StringUtils.containsAny(request.getHeader("Accept"), "text/html", "application/xhtml", "image/")) {
            return false;
        }

        return true;
    }

    @Override
    public void destroy() {
        // Usually, do nothing
    }

    @Override
    public Map<String, Collection<String>> get() {
        return THREAD_LOCAL.get();
    }

    @Override
    public synchronized void add(final String path, final String renditionName) {
        if (isEnabled()) {
            Collection<String> renditionNames = null;

            if (contains(path)) {
                renditionNames = get().get(path);
            }

            if (renditionNames == null) {
                renditionNames = new HashSet<>();
            }

            if (renditionName != null) {
                renditionNames.add(renditionName);
            }

            get().put(path, renditionNames);
        }
    }

    @Override
    public boolean isEnabled() {
        return THREAD_LOCAL.get() != null;
    }

    @Override
    public boolean contains(final String path) {
        if (isEnabled()) {
            return get().containsKey(path);
        } else {
            return false;
        }
    }
}