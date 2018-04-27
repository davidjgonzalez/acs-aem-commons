package com.adobe.acs.commons.remoteassets.impl.packages.listeners;

import com.day.cq.dam.api.DamConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InstalledAssetRenditionListener implements PathsTrackerListener {
    private static final Logger log = LoggerFactory.getLogger(InstalledAssetRenditionListener.class);

    private final List<String> paths = new ArrayList<>();

    public InstalledAssetRenditionListener() {
        super();
    }

    public List<String> getPaths() {
        return paths;
    }

    @Override
    public void onMessage(final Mode mode, final String action, final String path) {
        if (StringUtils.startsWith(path, DamConstants.MOUNTPOINT_ASSETS)
                && StringUtils.contains(path, "/jcr:content/renditions/")) {
            log.debug("Importing asset rendition [ {} ]", path);
            paths.add(path);
        }
    }

    @Override
    public void onError(final Mode mode, final String path, final Exception e) {

    }
}