package com.adobe.acs.commons.remoteassets.impl.packages.listeners;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;

import java.util.List;

public interface PathsTrackerListener extends ProgressTrackerListener {
    List<String> getPaths();
}
