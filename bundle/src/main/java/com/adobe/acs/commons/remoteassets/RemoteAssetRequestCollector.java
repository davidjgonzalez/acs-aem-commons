package com.adobe.acs.commons.remoteassets;

import aQute.bnd.annotation.ProviderType;

import java.util.Set;

@ProviderType
public interface RemoteAssetRequestCollector {
    Set<String> getPaths();

    void addPath(String path);

    boolean isEnabled();

    boolean contains(String path);
}
