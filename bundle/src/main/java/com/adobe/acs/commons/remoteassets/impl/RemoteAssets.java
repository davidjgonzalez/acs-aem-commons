/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2018 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.adobe.acs.commons.remoteassets.impl;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.commons.util.DamUtil;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.HashMap;

/**
 * Common functionality for Remote Assets.
 */
public class RemoteAssets {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssets.class);
    public static final String SERVICE_NAME = "remote-assets";

    public static final String NN_REMOTE = "remote";
    private static final String PN_IS_REMOTE_ASSET = "isRemoteAsset";
    private static final String PN_REMOTE_SYNC_FAILED = "remoteSyncFailed";

    /**
     * Private constructor.
     */
    private RemoteAssets() {
        throw new IllegalStateException("Utility class");
    }

    public static void setIsRemoteAsset(Resource resource, boolean value) {
        final ModifiableValueMap properties = getOrCreateRemoteProperties(resource);

        if (properties != null) {
            if (value) {
                properties.put(PN_IS_REMOTE_ASSET, value);
            } else {
                properties.remove(PN_IS_REMOTE_ASSET);
            }
        }
    }

    public static boolean isRemoteAsset(Resource resource) {
        final ModifiableValueMap properties = getOrCreateRemoteProperties(resource);

        if (properties != null) {
            return properties.get(PN_IS_REMOTE_ASSET, false);
        } else {
            return false;
        }
    }

    public static void setIsRemoteSyncFailed(Resource resource, Calendar value) {
        final ModifiableValueMap properties = getOrCreateRemoteProperties(resource);

        if (properties != null) {
            if (value != null) {
                properties.put(PN_REMOTE_SYNC_FAILED, value);
            } else {
                properties.remove(PN_REMOTE_SYNC_FAILED);
            }
        }
    }

    public static Calendar getRemoteSyncFailed(Resource resource) {
        final ModifiableValueMap properties = getOrCreateRemoteProperties(resource);

        if (properties != null) {
            return properties.get(PN_REMOTE_SYNC_FAILED, Calendar.class);
        } else {
            return null;
        }
    }

    private static ModifiableValueMap getOrCreateRemoteProperties(Resource assetResource) {
        final Asset asset = DamUtil.resolveToAsset(assetResource);
        if (asset == null) {
            return null;
        }

        assetResource = asset.adaptTo(Resource.class).getChild(JcrConstants.JCR_CONTENT);
        Resource remoteResource = assetResource.getChild(NN_REMOTE);
        if (remoteResource == null) {
            try {
                remoteResource = assetResource.getResourceResolver().create(assetResource, NN_REMOTE, new HashMap<>());
            } catch (PersistenceException e) {
                log.error("Could not create the [dam:Asset]/jcr:content/{} resource.", NN_REMOTE, e);
                return null;
            }
        }

        return remoteResource.adaptTo(ModifiableValueMap.class);
    }
}
