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

import com.adobe.acs.commons.fam.ActionManager;
import com.adobe.acs.commons.fam.ActionManagerFactory;
import com.adobe.acs.commons.remoteassets.RemoteAssetsBinarySync;
import com.adobe.acs.commons.remoteassets.RemoteAssetsConfig;
import com.adobe.acs.commons.remoteassets.RemoteAssetsRenditionsSync;
import com.adobe.granite.asset.api.RenditionHandler;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.dam.api.Rendition;
import com.day.cq.dam.commons.util.DamUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * Service to sync a remote asset's binaries a from remote server. Implements {@link RemoteAssetsBinarySync}.
 */
@Component(enabled = false)
@Service
public class RemoteAssetsBinarySyncImpl implements RemoteAssetsRenditionsSync {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetsBinarySyncImpl.class);

    @Reference
    private RemoteAssetsConfig config;

    @Reference
    private ActionManagerFactory actionManagerFactory;

    @Override
    public void syncAssetRenditions(ResourceResolver resourceResolver, String... assetPaths) {
        ActionManager actionManager = null;
        try {
            actionManager = config.getActionManager();
        } catch (LoginException e) {
            log.error("Could not aquire an action manager", e);
        }

        for (String assetPath : assetPaths) {
            try {
                final Resource assetResource = resourceResolver.getResource(assetPath);
                final Asset asset = DamUtil.resolveToAsset(assetResource);
                final String url = new URI(config.getServer() + asset.getPath() + "/_jcr_content/renditions/").toString();

                for (Rendition rendition : asset.getRenditions()) {
                    if (actionManager != null) {
                        actionManager.deferredWithResolver(rr -> {
                            config.applyEventUserData(rr);

                            fetchRendition(rr, rendition, url, asset);
                        });
                    } else {
                        try {
                            fetchRendition(resourceResolver, rendition, url, asset);
                        } catch (IOException e) {
                            log.error("Error transferring remote renditions [ {} ] to local server", rendition.getName(), e);
                        }
                    }
                }

                RemoteAssets.setIsRemoteAsset(assetResource, false);
                RemoteAssets.setIsRemoteSyncFailed(assetResource, null);

                resourceResolver.commit();

            } catch (Exception e) {
                log.error("Error transferring  [ {} ] remote asset to local server", assetPaths.length, e);

                try {
                    resourceResolver.revert();
                } catch (Exception re) {
                    log.error("Failed to rollback asset changes", re);
                }

                flagAssetAsFailedSync(resourceResolver, resourceResolver.getResource(assetPath));
            }
        }
    }

    private void fetchRendition(ResourceResolver resourceResolver, Rendition rendition, String urlPrefix, Asset asset) throws IOException {
        final long start = System.currentTimeMillis();

        if (StringUtils.isEmpty(rendition.getMimeType())) {
            return;
        }

        final String url = urlPrefix + rendition.getName();
        setRenditionOnAsset(url, rendition, asset, rendition.getName());

        log.debug("Synced rendition [ {} ] from remote server [ {} ] in [ {} ms ]",
                new String[]{rendition.getName(), url, String.valueOf(System.currentTimeMillis() - start)});
    }


    /**
     * Fetch binary from URL and set into the asset rendition.
     *
     * @param remoteUrl      String
     * @param assetRendition Rendition
     * @param asset          Asset
     * @param renditionName  String
     * @throws FileNotFoundException exception
     */
    private void setRenditionOnAsset(String remoteUrl, Rendition assetRendition, Asset asset, String renditionName)
            throws IOException {

        log.debug("Syncing from remote asset url {}", remoteUrl);

        Executor executor = this.config.getRemoteAssetsHttpExecutor();

        try (InputStream inputStream = executor.execute(Request.Get(remoteUrl)).returnContent().asStream()) {
            Map<String, Object> props = new HashMap<>();
            props.put(RenditionHandler.PROPERTY_RENDITION_MIME_TYPE, assetRendition.getMimeType());
            asset.addRendition(renditionName, inputStream, props);
        } catch (HttpResponseException fne) {
            if (DamConstants.ORIGINAL_FILE.equals(renditionName) || fne.getStatusCode() != HTTP_NOT_FOUND) {
                throw fne;
            }

            asset.removeRendition(renditionName);
            log.warn("Rendition '{}' not found on remote environment. Removing local rendition.", renditionName);
        }
    }

    /**
     * Sets a property on the resource if the asset sync failed.
     *
     * @param resource Resource
     * @return Resource
     */
    private Resource flagAssetAsFailedSync(ResourceResolver resourceResolverWithServiceContext, Resource resource) {
        try {
            final Resource resourceWithServiceContext = resourceResolverWithServiceContext.getResource(resource.getPath());

            RemoteAssets.setIsRemoteSyncFailed(resourceWithServiceContext, Calendar.getInstance());

            resourceResolverWithServiceContext.commit();

            return resourceWithServiceContext;
        } catch (Exception e) {
            log.error("Error flagging remote asset '{}' as failed - asset may attempt to sync numerous times in succession", resource.getPath(), e);
            try {
                resourceResolverWithServiceContext.revert();
            } catch (Exception re) {
                log.error("Failed to rollback asset changes", re);
            }
        }
        return resource;
    }
}
