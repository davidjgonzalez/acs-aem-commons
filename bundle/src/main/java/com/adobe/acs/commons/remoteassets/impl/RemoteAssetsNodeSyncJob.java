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

import com.adobe.acs.commons.remoteassets.RemoteAssetsConfig;
import com.adobe.acs.commons.remoteassets.RemoteAssetsMBean;
import com.adobe.acs.commons.remoteassets.RemoteAssetsSync;
import com.adobe.acs.commons.remoteassets.RemoteTagsSync;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Job that will sync asset nodes based on OSGi configuration. Implements {@link Runnable}.
 */
@Component(
        label = "ACS AEM Commons - Remote Assets Sync Job",
        description = "Scheduled Service that runs the Remote Assets node sync.",
        metatype = true
)
@Properties({
        @Property(
                label = "Cron expression defining when this Scheduled Service will run",
                name = "scheduler.expression",
                description = "Default value ('0 0,4,8,12,16,20 * * *') will run this job every 4 hours starting at 00:00.",
                value = "0 0,4,8,12,16,20 * * *"
        ),
        @Property(
                name = "scheduler.concurrent",
                boolValue = false,
                propertyPrivate = true
        ),
        @Property(
                name = "jmx.objectname",
                value = "com.adobe.acs.commons:type=Remote Asset Sync"
        )
})
@Service
public class RemoteAssetsNodeSyncJob implements Runnable, RemoteAssetsMBean {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetsNodeSyncJob.class);

    @Reference
    private RemoteAssetsSync remoteAssetsSync;

    @Reference
    private RemoteTagsSync remoteTagsSync;

    // This reference acts as the dependency to enable this scheduled service.
    @Reference
    private RemoteAssetsConfig config;

    /**
     * @see Runnable#run().
     */
    @Override
    public final void run() {
        syncAll();
    }

    @Override
    public void syncAll() {
       syncTags();
       syncAssets();
    }

    @Override
    public void syncTags() {
        final long start = System.currentTimeMillis();

        ResourceResolver serviceResourceResolver = null;

        try {
            serviceResourceResolver = config.getResourceResolver();

            log.debug("Begin syncing tags from [ {} ]", config.getServer());

            long tagCount = remoteTagsSync.syncTags(serviceResourceResolver);

            log.info("Synced [ {} ] tags in [ {} seconds ]", tagCount, ((System.currentTimeMillis() - start) / 1000));

        } finally {
            if (serviceResourceResolver != null) {
                serviceResourceResolver.close();
            }
        }
    }

    @Override
    public void syncAssets() {
        final long start = System.currentTimeMillis();

        ResourceResolver serviceResourceResolver = null;

        try {
            serviceResourceResolver = config.getResourceResolver();

            log.debug("Begin syncing assets from [ {} ]", config.getServer());

            long assetCount = remoteAssetsSync.syncAssets(serviceResourceResolver);

            log.info("Synced [ {} ] assets in [ {} seconds ]", assetCount, ((System.currentTimeMillis() - start) / 1000));

        } finally {
            if (serviceResourceResolver != null) {
                serviceResourceResolver.close();
            }
        }
    }
}
