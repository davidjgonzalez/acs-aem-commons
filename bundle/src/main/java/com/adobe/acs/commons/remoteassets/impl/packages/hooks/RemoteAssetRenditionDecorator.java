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
package com.adobe.acs.commons.remoteassets.impl.packages.hooks;

import com.adobe.acs.commons.remoteassets.*;
import com.adobe.acs.commons.remoteassets.impl.RemoteAssets;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.dam.commons.util.DamUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceDecorator;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ResourceDecorator that instruments remote assets to sync binaries as needed.
 * This "decorator" is used to detect the first time a "remote" asset is
 * referenced by the system and sync that asset from the remote server to
 * make it now a "true" asset.
 * <p>
 * The dependency on the RemoteAssetConfig ensures this is only active when this feature is configured.
 */
@Component
@Service
public class RemoteAssetRenditionDecorator implements ResourceDecorator {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetRenditionDecorator.class);

    @Reference
    private RemoteAssetsRenditionsSync assetsRenditionsSync;

    @Reference
    private RemoteAssetsConfig config;

    @Reference
    private RemoteAssetRequestCollector remoteAssetCollector;

    @Reference
    private RemoteAssetsRenditions remoteAssetsRenditions;

    @Reference
    private SyncStateManager syncStateManager;

    private static final ThreadLocal<Boolean> THREAD_LOCAL = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            set(false);
            return get();
        }
    };

    /**
     * When resolving a remote asset, first sync the asset from the remote server.
     *
     * @param resource The resource being resolved.
     * @return The current resource.  If the resource is a "remote" asset, it will
     * first be converted to a true local AEM asset by sync'ing in the rendition
     * binaries from the remote server.
     */
    @Override
    public Resource decorate(final Resource resource) {
        if (THREAD_LOCAL.get()) {
            return resource;
        }

        THREAD_LOCAL.set(true);

        if (accepts(resource)) {

            final Asset asset = DamUtil.resolveToAsset(resource);

            ResourceResolver serviceResourceResolver = null;

            try {
                serviceResourceResolver = config.getResourceResolver();

                final Resource renditionsFolderResource = getAssetRenditionsFolderResource(asset);
                final String renditionName = findRenditionName(serviceResourceResolver, asset);

                final Executor executor = config.getRemoteAssetsHttpExecutor();
                final Request remoteRequest = Request.Get(config.getRemoteURI(renditionsFolderResource.getPath() + "/" + renditionName));

                final HttpResponse remoteResponse = executor.execute(remoteRequest).returnResponse();

                if (remoteResponse.getStatusLine().getStatusCode() == 200) {

                    try (InputStream in = remoteResponse.getEntity().getContent()) {
                        if (renditionsFolderResource.getChild(renditionName) != null) {
                            serviceResourceResolver.delete(renditionsFolderResource.getChild(renditionName));
                        }

                        asset.addRendition(renditionName, in, remoteResponse.getEntity().getContentType().getValue());

                        remoteAssetCollector.add(asset.getPath(), renditionName);
                    }
                } else {
                    remoteAssetCollector.add(asset.getPath(), null);
                }

            } catch (Exception e) {

                log.error("Error sync'ing rendition via the Resource Decorator hook", e);

            } finally {
                if (serviceResourceResolver != null) {

                    if (serviceResourceResolver.hasChanges()) {
                        try {
                            serviceResourceResolver.commit();
                            resource.getResourceResolver().refresh();
                        } catch (PersistenceException e) {
                            log.error("Unable to persist changes to decorator", e);
                        }
                    }

                    serviceResourceResolver.close();
                }
            }
        }

        THREAD_LOCAL.remove();

        return resource;

    }


    private boolean accepts(final Resource resource) {
        if (!remoteAssetCollector.isEnabled()) {
            return false;
        }

        if (!resource.isResourceType(DamConstants.NT_DAM_ASSETCONTENT)) {
            return false;
        }

        final Asset asset = DamUtil.resolveToAsset(resource);

        if (asset == null || asset.isSubAsset()) {
            return false;
        }

        if (syncStateManager.contains(asset.getPath())) {
            return false;
        }

        if (!RemoteAssets.isRemoteAsset(resource.getParent())) {
            return false;
        }

        return true;
    }


    /**
     * @param resource The resource being resolved.
     * @param request  HttpServletRequest
     * @return The current resource.  If the resource is a "remote" asset, it will
     * first be converted to a true local AEM asset by sync'ing in the rendition
     * binaries from the remote server.
     * @deprecated When resolving a remote asset, first sync the asset from the remote server.
     */
    @Deprecated
    @Override
    public Resource decorate(final Resource resource, final HttpServletRequest request) {
        return this.decorate(resource);
    }

    public String findRenditionName(ResourceResolver resourceResolver, Asset asset) {
        Resource renditionsFolder = getAssetRenditionsFolderResource(asset);

        if (renditionsFolder != null) {
            final Pattern pattern = Pattern.compile("cq5dam\\.web\\.(.*)");
            for (Resource rendition : renditionsFolder.getChildren()) {
                final Matcher matcher = pattern.matcher(rendition.getName());
                if (matcher.matches()) {
                    return rendition.getName();
                }
            }
        }

        return null;
    }

    public Resource getAssetRenditionsFolderResource(Asset asset) {
       return asset.adaptTo(ResourceResolver.class).getResource(asset.getPath() + "/" + JcrConstants.JCR_CONTENT + "/" + DamConstants.RENDITIONS_FOLDER);
    }
}
