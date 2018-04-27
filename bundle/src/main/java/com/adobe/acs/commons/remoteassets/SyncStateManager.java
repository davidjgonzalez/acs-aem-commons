package com.adobe.acs.commons.remoteassets;

import aQute.bnd.annotation.ProviderType;

@ProviderType
public interface SyncStateManager {
    void add(String path);
    boolean contains(String path);
    void remove(String path);
}
