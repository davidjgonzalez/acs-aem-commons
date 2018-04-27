package com.adobe.acs.commons.remoteassets.impl.packages.remotepackages;

import com.google.gson.JsonArray;

import java.util.List;

public abstract class RemotePackage {
    protected static final String PACKAGE_GROUP = "acs-commons/remote-assets";
    protected static final String PACKAGE_VERSION = "1.0.0";

    protected String path;
    protected final String name;
    protected final String group;
    protected final String version;
    protected final List<String> syncPaths;

    public RemotePackage(String name, String group, String version, List<String> syncPaths) {
        this.name = name;
        this.group = group;
        this.version = version;
        this.syncPaths = syncPaths;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getGroup() {
        return group;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return "ACS AEM Commons - Remote Assets Sync";
    }

    public String getFilter() {
        final JsonArray json = new JsonArray();
        return json.toString();
    }

    public boolean isEmpty() {
        return this.syncPaths == null || this.syncPaths.size() == 0;
    }
}