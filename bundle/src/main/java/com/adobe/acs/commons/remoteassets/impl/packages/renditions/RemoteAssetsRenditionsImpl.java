package com.adobe.acs.commons.remoteassets.impl.packages.renditions;

import com.adobe.acs.commons.remoteassets.RemoteAssetsConfig;
import com.adobe.acs.commons.remoteassets.RemoteAssetsRenditions;
import com.adobe.acs.commons.remoteassets.SyncStateManager;
import com.adobe.acs.commons.remoteassets.impl.packages.hooks.RemoteAssetsRenditionServlet;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Service
public class RemoteAssetsRenditionsImpl implements RemoteAssetsRenditions {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetsRenditionsImpl.class);
    private static final String REMOTE_RENDITIONS_SYNCING_NAME = "Remote renditions synching";
    private static final String PLACEHOLDER_PATH = "/apps/acs-commons/dam/remote-assets/remote_asset.syncing";

    @Reference
    private SyncStateManager syncStateManager;

    @Reference
    private MimeTypeService mimeTypeService;

    @Reference
    private RemoteAssetsConfig config;


    public void createPlaceholderRendition(Asset asset, String... renditionNames) throws PersistenceException {
        final ResourceResolver resourceResolver = asset.adaptTo(ResourceResolver.class);
        final Resource renditionsFolder = resourceResolver.getResource(asset.getPath() + "/" + JcrConstants.JCR_CONTENT + "/" + DamConstants.RENDITIONS_FOLDER);

        if (renditionsFolder != null) {
            for (String renditionName : renditionNames) {
                //final String mimeType = mimeTypeService.getMimeType(renditionName);

                if (renditionsFolder.getChild(renditionName) == null) {
                    Map<String, Object> properties = new HashMap<>();
                    properties.put(JcrConstants.JCR_PRIMARYTYPE, JcrResourceConstants.NT_SLING_FOLDER);
                    properties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, RemoteAssetsRenditionServlet.RESOURCE_TYPE);

                    Resource rendition = resourceResolver.create(renditionsFolder, renditionName, properties);
                    log.debug("Created placeholder rendition [ {} ]", rendition.getPath());
                } else {
                    log.trace("Placeholder rendition already exists at [ {} ]. Skipping creation...", renditionsFolder.getPath() + "/" + renditionName);

                }
            }
        }
    }


    public void setPlaceholderRenditions(final Asset asset) {
        if (asset != null && asset.getRendition(DamConstants.ORIGINAL_FILE) != null) {
            log.trace("New base asset note already has an original rendition... skipping");
            return;
        }

        final String mimeType = asset.getMimeType();
        final String extension = mimeTypeService.getExtension(mimeType);

        final List<String> renditionNames = config.getLazyAssetRenditions();

        renditionNames.stream()
                .map(renditionPattern -> org.apache.commons.lang3.StringUtils.replace(renditionPattern, "{extension}", extension))
                .forEach(renditionName -> {
                    try {
                        createPlaceholderRendition(asset, renditionName);
                    } catch (PersistenceException e) {
                        log.error("Could not create rendition [ {} ] for asset [ {} ]", renditionName, asset.getPath());
                    }
                });
    }

    public void addSyncingRendition(Asset asset) {
        final ResourceResolver resourceResolver = asset.adaptTo(ResourceResolver.class);
        final Resource placeholderResource = resourceResolver.getResource(PLACEHOLDER_PATH);
        final InputStream in = placeholderResource.adaptTo(InputStream.class);

        asset.addRendition(REMOTE_RENDITIONS_SYNCING_NAME, in, "image/jpeg");
    }

    public void removeSyncingRendition(Asset asset) {
        if (asset.getRendition(REMOTE_RENDITIONS_SYNCING_NAME) != null) {
            asset.removeRendition(REMOTE_RENDITIONS_SYNCING_NAME);
        }
    }
}
