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

import com.adobe.acs.commons.remoteassets.RemoteAssetRequestCollector;
import com.adobe.acs.commons.remoteassets.RemoteAssetsConfig;
import com.adobe.acs.commons.remoteassets.RemoteAssetsRenditionsSync;
import com.adobe.acs.commons.remoteassets.SyncStateManager;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceDecorator;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * ResourceDecorator that instruments remote assets to sync binaries as needed.
 * This "decorator" is used to detect the first time a "remote" asset is
 * referenced by the system and sync that asset from the remote server to
 * make it now a "true" asset.
 *
 * The dependency on the RemoteAssetConfig ensures this is only active when this feature is configured.
 */
@Component(
        label = "ACS AEM Commons - Remote Assets - Asset Resource Decorator",
        description = "Captures a request for a remote asset so that the binary can be sync'd to the current server making it a true local asset"
)
@Service
public class RemoteAssetDecorator implements ResourceDecorator {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetDecorator.class);

    @Reference
    private RemoteAssetsRenditionsSync assetsRenditionsSync;

    @Reference
    private RemoteAssetsConfig config;

    @Reference
    private RemoteAssetRequestCollector remoteAssetCollector;

    @Reference
    private SyncStateManager syncStateManager;

    private static final ThreadLocal<Boolean> THREAD_LOCAL = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
            set(false);
            return get();
        }
    };

    /**
     * When resolving a remote asset, first sync the asset from the remote server.
     * @param resource The resource being resolved.
     * @return The current resource.  If the resource is a "remote" asset, it will
     * first be converted to a true local AEM asset by sync'ing in the rendition
     * binaries from the remote server.
     */
    @Override
    public Resource decorate(final Resource resource) {
        if (!this.accepts(resource)) {
            return resource;
        }

        final Asset asset = resource.adaptTo(Asset.class);

        synchronized (syncStateManager) {
            if (!syncStateManager.contains(asset.getPath())) {
                syncStateManager.add(asset.getPath());
                // ENSURE THIS IS REMOVED AFTER THE RENDITIONS ARE SYNC'D AND
            } else {
                return resource;
            }
        }

        if (remoteAssetCollector.isEnabled()) {
            remoteAssetCollector.addPath(asset.getPath());
            log.debug("Collecting asset [ {} ] to bulk retrieve renditions at the end of the request.", asset.getPath());
            return resource;
        }

        ResourceResolver serviceResourceResolver =  null;

        try {
            serviceResourceResolver = config.getResourceResolver();
            assetsRenditionsSync.syncAssetRenditions(serviceResourceResolver, asset.getPath());
        } catch (Exception e) {
            log.error("Failed to sync remote renditions for asset [ {} ]", resource.getPath(), e);
        } finally {
            syncStateManager.remove(asset.getPath());

            if (serviceResourceResolver != null) {
                serviceResourceResolver.close();
            }

            // Refresh the requesting context
            resource.getResourceResolver().refresh();
        }

        return resource;
    }

    /**
     * @deprecated
     * When resolving a remote asset, first sync the asset from the remote server.
     * @param resource The resource being resolved.
     * @param request HttpServletRequest
     * @return The current resource.  If the resource is a "remote" asset, it will
     * first be converted to a true local AEM asset by sync'ing in the rendition
     * binaries from the remote server.
     */
    @Deprecated
    @Override
    public Resource decorate(final Resource resource, final HttpServletRequest request) {
        return this.decorate(resource);
    }

    /**
     * Check if this resource is a remote resource.
     * @param resource Resource to check
     * @return true if resource is remote, else false
     */
    private boolean accepts(final Resource resource) {
        if (THREAD_LOCAL.get()) {
            // This means that this decoration is happening INSIDE this accept method via one of these other checks, so skip!
            return false;
        }
        THREAD_LOCAL.set(true);

         try {
             if (resource == null) {
                 return false;
             }

             if (syncStateManager.contains(resource.getPath())) {
                 return false;
             }

             if (!resource.getPath().startsWith(DamConstants.MOUNTPOINT_ASSETS)) {
                 return false;
             }

             if (!DamConstants.NT_DAM_ASSET.equals(resource.getValueMap().get(JcrConstants.JCR_PRIMARYTYPE, String.class))) {
                 return false;
             }

             if (!RemoteAssets.isRemoteAsset(resource)) {
                 return false;
             }

             /*
             if (remoteAssetCollector.isEnabled() && remoteAssetCollector.contains(resource.getPath())) {
                 return false;
             }
            */

             final Calendar failureThreshold = RemoteAssets.getRemoteSyncFailed(resource);
             if (failureThreshold != null) {
                 final Calendar now = Calendar.getInstance();
                 failureThreshold.add(Calendar.MINUTE, config.getRetryDelay());

                 if (now.before(failureThreshold)) {
                     log.debug("Resource is still in a quiet period due to a sync failure; refusing to decorate");
                     return false;
                 }
             }

             if (!StringUtils.startsWithAny(resource.getPath(),config.getDamSyncPaths().toArray(new String[config.getDamSyncPaths().size()]))) {
                 return false;
             }


             final String userId = resource.getResourceResolver().getUserID();

             //if (!UserConstants.DEFAULT_ADMIN_ID.equals(userId)) {
             if (this.config.getWhitelistedServiceUsers().contains(userId)) {
                 return true;
             }


             final User currentUser = resource.getResourceResolver().adaptTo(User.class);
             if (currentUser != null && !currentUser.isSystemUser()) {
                 return true;
             } else {
                 log.trace("Avoiding binary sync b/c this is a non-whitelisted service user: {}", userId);
             }

//            } else {
             // log.debug("Avoiding binary sync for admin user");
             //}

             return false;
         } finally {
             THREAD_LOCAL.remove();
         }
    }
}
