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
package com.adobe.acs.commons.remoteassets;

import org.apache.sling.api.resource.ResourceResolver;

import java.util.Collection;
import java.util.Map;

/**
 * Service that syncs remote asset renditions from the remote server.
 */
public interface RemoteAssetsRenditionsSync {

    /**
     * Sync remote asset renditions from remote server.
     */
    void syncAssetRenditions(ResourceResolver resourceResolver, String... assetPaths);

    void syncAssetRenditions(ResourceResolver resourceResolver, Map<String, Collection<String>> assetAndExcludedRenditions);

}
