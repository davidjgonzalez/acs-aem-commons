package com.adobe.acs.commons.remoteassets.impl.packages.listeners;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InstalledTagListener implements PathsTrackerListener {
    private static final Logger log = LoggerFactory.getLogger(InstalledTagListener.class);

    private final List<String> paths = new ArrayList<>();
    private final String[] TAG_PATHS = new String[]{"/content/cq:tags/", "/etc/tags/"};

    public InstalledTagListener() {
        super();
    }

    public List<String> getPaths() {
        return paths;
    }

    @Override
    public void onMessage(final Mode mode, final String action, final String path) {
        if (StringUtils.startsWithAny(path, TAG_PATHS) &&
                !StringUtils.endsWith(path,".content.xml") &&
                !StringUtils.contains(path, "/jcr:content/")) {
            paths.add(path);
        }
    }

    @Override
    public void onError(final Mode mode, final String path, final Exception e) {

    }
}