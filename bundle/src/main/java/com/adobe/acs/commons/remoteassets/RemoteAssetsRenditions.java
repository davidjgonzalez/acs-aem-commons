package com.adobe.acs.commons.remoteassets;

import com.day.cq.dam.api.Asset;
import org.apache.sling.api.resource.PersistenceException;

public interface RemoteAssetsRenditions {

    void setPlaceholderRenditions(Asset asset);

    void createPlaceholderRendition(Asset asset, String... renditionNames) throws PersistenceException;

    void addSyncingRendition(Asset asset);

    void removeSyncingRendition(Asset asset);
}
