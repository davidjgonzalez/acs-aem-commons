package com.adobe.acs.commons.remoteassets.impl.packages.filters;


import com.adobe.acs.commons.remoteassets.RemoteAssetRequestCollector;
import com.adobe.acs.commons.remoteassets.RemoteAssetsConfig;
import com.adobe.acs.commons.remoteassets.RemoteAssetsRenditionsSync;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;


@Component(immediate = true)
@Properties({
        @Property(name = "sling.filter.scope",
                  value = {"request"}),

        @Property(name = "service.ranking",
                  intValue = 200000)
})
@Service

public class RemoteAssetCollectorFilter implements Filter, RemoteAssetRequestCollector {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetCollectorFilter.class);

    private static final ThreadLocal<Set<String>> THREAD_LOCAL = new ThreadLocal<Set<String>>() {
        @Override
        protected Set<String> initialValue() {
            return null;
        }
    };

    @Reference
    private RemoteAssetsConfig config;

    @Reference
    private RemoteAssetsRenditionsSync remoteAssetsRenditionsSync;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Usually, do nothing
    }

    @Override
    public final void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {

        THREAD_LOCAL.set(new ConcurrentSkipListSet<String>());

        try {
            chain.doFilter(request, response);

            ResourceResolver serviceResourceResolver = null;

            try {
                serviceResourceResolver = config.getResourceResolver();

                if (getPaths().size() > 0) {
                    final String[] assetPaths = getPaths().toArray(new String[getPaths().size()]);

                    if (log.isDebugEnabled()) {
                        log.debug("Asychronously synching collected renditions for [ {} ] assets.", assetPaths.length);
                    }

                    remoteAssetsRenditionsSync.syncAssetRenditions(serviceResourceResolver, assetPaths);
                }

            } finally {
                if (serviceResourceResolver != null) {
                    serviceResourceResolver.close();
                }
            }
        } finally {
            THREAD_LOCAL.remove();
        }
    }

    @Override
    public void destroy() {
        // Usually, do nothing
    }

    @Override
    public Set<String> getPaths() {
        return THREAD_LOCAL.get();
    }

    @Override
    public synchronized void addPath(final String path) {
        if (isEnabled() && !THREAD_LOCAL.get().contains(path)) {
            THREAD_LOCAL.get().add(path);
        }
    }

    @Override
    public boolean isEnabled() {
        return THREAD_LOCAL.get() != null;
    }

    @Override
    public boolean contains(final String path) {
        return getPaths().contains(path);
    }
}