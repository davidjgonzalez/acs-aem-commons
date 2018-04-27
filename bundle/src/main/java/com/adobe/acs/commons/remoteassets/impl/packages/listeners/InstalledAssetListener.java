package com.adobe.acs.commons.remoteassets.impl.packages.listeners;

import com.day.cq.dam.api.DamConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InstalledAssetListener implements PathsTrackerListener {
    private static final Logger log = LoggerFactory.getLogger(InstalledAssetListener.class);

    private final ResourceResolver resourceResolver;
    private final List<String> paths = new ArrayList<>();

    public InstalledAssetListener(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public List<String> getPaths() {
        return paths;
    }

    @Override
    public void onMessage(final Mode mode, final String action, final String path) {
        if (Mode.TEXT.equals(mode) &&
                "A".equals(action) &&
                StringUtils.startsWith(path, DamConstants.MOUNTPOINT_ASSETS)
                && !StringUtils.contains(path, "/" + JcrConstants.JCR_CONTENT + "/")) {
            final Resource resource = resourceResolver.getResource(path);

            if (resource != null &&
                    resource.isResourceType(DamConstants.NT_DAM_ASSET)) {
                log.trace("Found possible new asset during package install at [ {} ]", path);
                paths.add(path);
            }
        }
    }

    @Override
    public void onError(final Mode mode, final String path, final Exception e) {

    }
}