package com.adobe.acs.commons.remoteassets;

import aQute.bnd.annotation.ProviderType;

import java.util.Collection;
import java.util.Map;

@ProviderType
public interface RemoteAssetRequestCollector {
    Map<String, Collection<String>> get();

    void add(String assetPath, String renditionName);

    boolean isEnabled();

    boolean contains(String assetPath);
}
