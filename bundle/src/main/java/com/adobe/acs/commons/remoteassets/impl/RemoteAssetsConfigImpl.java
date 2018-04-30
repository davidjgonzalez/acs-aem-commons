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
import com.adobe.acs.commons.remoteassets.RemoteAssetsConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration service for Remote Asset feature. Implements {@link RemoteAssetsConfig}.
 */
@Component(
        immediate = true,
        metatype = true,
        label = "ACS AEM Commons - Remote Assets - Config",
        policy = ConfigurationPolicy.REQUIRE
)
@Service()
public class RemoteAssetsConfigImpl implements RemoteAssetsConfig {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetsConfigImpl.class);
    private static final boolean DEFAULT_ALLOW_INSECURE = false;

    @Property(label = "Server")
    private static final String SERVER_PROP = "server.url";

    @Property(label = "Username")
    private static final String USERNAME_PROP = "server.user";

    @Property(label = "Password")
    private static final String PASSWORD_PROP = "server.pass";

    @Property(label = "Allow Insecure Connection", description = "Allow non-https connection to remote assets server, "
            + "allowing potential compromise of connection credentials", boolValue = DEFAULT_ALLOW_INSECURE)
    private static final String ALLOW_INSECURE_PROP = "server.insecure";

    @Property(
            label = "Tag Sync Paths",
            description = "Paths to sync tags from the remote server (e.g. /etc/tags/asset)",
            cardinality = Integer.MAX_VALUE,
            value = {}
    )
    private static final String TAG_SYNC_PATHS_PROP = "tag.paths";

    @Property(
            label = "Asset Sync Paths",
            description = "Paths to sync assets from the remote server (e.g. /content/dam)",
            cardinality = Integer.MAX_VALUE,
            value = {}
    )
    private static final String DAM_SYNC_PATHS_PROP = "dam.paths";

    @Property(
            label = "Asset Sync Renditions (Eager)",
            description = "Renditions to initially sync assets from the remote server.",
            cardinality = Integer.MAX_VALUE,
            value = {}
    )
    private static final String DAM_ASSET_RENDITIONS_EAGER = "dam.renditions.eager";


    @Property(
            label = "Asset Sync Renditions (Lazy)",
            description = "Renditions to lazily sync assets from the remote server.",
            cardinality = Integer.MAX_VALUE,
            value = {}
    )
    private static final String DAM_ASSET_RENDITIONS_LAZY = "dam.renditions.lazy";


    @Property(
            label = "Failure Retry Delay (in minutes)",
            description = "Number of minutes the server will wait to attempt to sync a remote asset that failed "
                    + "a sync attempt (minimum 1)",
            intValue = 15
    )
    private static final String RETRY_DELAY_PROP = "retry.delay";

    @Property(
            label = "Number of Assets to Sync Before Saving",
            description = "Number of asset nodes to sync before saving and refreshing the session during a node "
                    + "sync. The lower the number, the longer the sync will take (default 100)",
            intValue = 100
    )
    private static final String SAVE_INTERVAL_PROP = "save.interval";

    @Property(
            label = "Event User Data",
            description = "The event user data that will be set during all JCR manipulations performed by "
                    + "remote assets. This can be used in workflow launchers that listen to DAM paths (such as "
                    + "for DAM Update Assets) to exclude unnecessary processing such as rendition generation.",
            value = "changedByWorkflowProcess")
    private static final String EVENT_USER_DATA_PROP = "event.user.data";

    @Property(
            label = "Whitelisted Service Users",
            description = "Service users that are allowed to trigger remote asset binary syncs. By default, service "
                    + "user activity never triggers an asset binary sync.",
            cardinality = Integer.MAX_VALUE,
            value = {}
    )
    private static final String WHITELISTED_SERVICE_USERS_PROP = "whitelisted.service.users";

    private String server = StringUtils.EMPTY;
    private String username = StringUtils.EMPTY;
    private String password = StringUtils.EMPTY;
    private boolean allowInsecureRemote = false;
    private List<String> tagSyncPaths = new ArrayList<>();
    private List<String> damSyncPaths = new ArrayList<>();
    private List<String> eagerAssetRenditions = new ArrayList<>();
    private List<String> lazyAssetRenditions = new ArrayList<>();

    private Integer retryDelay;
    private Integer saveInterval;
    private String eventUserData = StringUtils.EMPTY;
    private Set<String> whitelistedServiceUsers = new HashSet<>();

    private Executor remoteAssetsHttpExecutor;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private ActionManagerFactory actionManagerFactory;

    /**
     * Method to run on activation.
     *
     * @param properties OSGi Component properties
     */
    @Activate
    @Modified
    private void activate(final Map<String, Object> properties) {
        this.server = PropertiesUtil.toString(properties.get(SERVER_PROP), StringUtils.EMPTY);
        if (StringUtils.isBlank(this.server)) {
            throw new IllegalArgumentException("Remote server must be specified");
        }

        this.username = PropertiesUtil.toString(properties.get(USERNAME_PROP), StringUtils.EMPTY);
        if (StringUtils.isBlank(this.username)) {
            throw new IllegalArgumentException("Remote server username must be specified");
        }

        this.password = PropertiesUtil.toString(properties.get(PASSWORD_PROP), StringUtils.EMPTY);
        if (StringUtils.isBlank(this.password)) {
            throw new IllegalArgumentException("Remote server password must be specified");
        }

        this.allowInsecureRemote = PropertiesUtil.toBoolean(properties.get(ALLOW_INSECURE_PROP), DEFAULT_ALLOW_INSECURE);

        this.tagSyncPaths = Stream.of(PropertiesUtil.toStringArray(properties.get(TAG_SYNC_PATHS_PROP), new String[0]))
                .filter(item -> StringUtils.isNotBlank(item))
                .collect(Collectors.toList());

        this.damSyncPaths = Stream.of(PropertiesUtil.toStringArray(properties.get(DAM_SYNC_PATHS_PROP), new String[0]))
                .filter(item -> StringUtils.isNotBlank(item))
                .collect(Collectors.toList());

        this.eagerAssetRenditions = Stream.of(PropertiesUtil.toStringArray(properties.get(DAM_ASSET_RENDITIONS_EAGER), new String[0]))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());


        this.lazyAssetRenditions = Stream.of(PropertiesUtil.toStringArray(properties.get(DAM_ASSET_RENDITIONS_LAZY), new String[0]))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        this.retryDelay = PropertiesUtil.toInteger(properties.get(RETRY_DELAY_PROP), 1);
        this.saveInterval = PropertiesUtil.toInteger(properties.get(SAVE_INTERVAL_PROP), 100);
        this.eventUserData = PropertiesUtil.toString(properties.get(EVENT_USER_DATA_PROP), StringUtils.EMPTY);
        this.whitelistedServiceUsers = Stream.of(PropertiesUtil.toStringArray(properties.get(WHITELISTED_SERVICE_USERS_PROP), new String[0]))
                .filter(item -> StringUtils.isNotBlank(item))
                .collect(Collectors.toSet());

        buildRemoteHttpExecutor();
    }

    /**
     * @return String
     * @see RemoteAssetsConfig#getServer()
     */
    @Override
    public String getServer() {
        return this.server;
    }

    /**
     * @return String
     * @see RemoteAssetsConfig#getUsername()
     */
    @Override
    public String getUsername() {
        return this.username;
    }

    /**
     * @return String
     * @see RemoteAssetsConfig#getPassword()
     */
    @Override
    public String getPassword() {
        return this.password;
    }

    /**
     * @return List<String>
     * @see RemoteAssetsConfig#getTagSyncPaths()
     */
    @Override
    public List<String> getTagSyncPaths() {
        return this.tagSyncPaths;
    }

    /**
     * @return List<String>
     * @see RemoteAssetsConfig#getDamSyncPaths()
     */
    @Override
    public List<String> getDamSyncPaths() {
        return this.damSyncPaths;
    }

    @Override
    public List<String> getEagerAssetRenditions() {
        return eagerAssetRenditions;
    }

    /**
     * @return Integer
     * @see RemoteAssetsConfig#getRetryDelay()
     */
    @Override
    public Integer getRetryDelay() {
        return this.retryDelay;
    }

    /**
     * @return Integer
     * @see RemoteAssetsConfig#getSaveInterval()
     */
    @Override
    public Integer getSaveInterval() {
        return this.saveInterval;
    }

    /**
     * @return String
     * @see RemoteAssetsConfig#getEventUserData()
     */
    @Override
    public String getEventUserData() {
        return this.eventUserData;
    }

    /**
     * @return String
     * @see RemoteAssetsConfig#getWhitelistedServiceUsers()
     */
    @Override
    public Set<String> getWhitelistedServiceUsers() {
        return this.whitelistedServiceUsers;
    }

    /**
     * @return Executor
     * @see RemoteAssetsConfig#getRemoteAssetsHttpExecutor()
     */
    @Override
    public Executor getRemoteAssetsHttpExecutor() {
        return remoteAssetsHttpExecutor;
    }

    /**
     * @see RemoteAssetsConfig#getResourceResolver()
     */
    @Override
    public ResourceResolver getResourceResolver() {
        try {
            Map<String, Object> userParams = new HashMap<>();
            userParams.put(ResourceResolverFactory.SUBSERVICE, RemoteAssets.SERVICE_NAME);
            ResourceResolver resourceResolver = this.resourceResolverFactory.getServiceResourceResolver(userParams);
            applyEventUserData(resourceResolver);
            return resourceResolver;
        } catch (Exception e) {
            log.error("Remote assets functionality cannot be enabled - service user login failed");
            throw new RemoteAssetsServiceException(e);
        }
    }

    /**
     * @see RemoteAssetsConfig#closeResourceResolver(ResourceResolver)
     */
    @Override
    public void closeResourceResolver(ResourceResolver resourceResolver) {
        if (resourceResolver != null) {
            resourceResolver.close();
        }
    }

    @Override
    public synchronized ActionManager getActionManager() throws LoginException {
        final String ACTION_MANAGER_NAME = "ACS AEM Commons - Remote Assets Sync";

        if (actionManagerFactory.hasActionManager(ACTION_MANAGER_NAME)) {
            return actionManagerFactory.getActionManager(ACTION_MANAGER_NAME);
        } else {
            return actionManagerFactory.createTaskManager(ACTION_MANAGER_NAME, getResourceResolver(), getSaveInterval());
        }
    }

    public void applyEventUserData(ResourceResolver resourceResolver) {
        if (StringUtils.isNotBlank(this.getEventUserData())) {
            Session session = resourceResolver.adaptTo(Session.class);
            try {
                session.getWorkspace().getObservationManager().setUserData(this.getEventUserData());
            } catch (RepositoryException e) {
                log.error("Could not set user event data [ {} ] on the session.", getEventUserData());
            }
        }
    }

    @Override
    public List<String> getLazyAssetRenditions() {
        return lazyAssetRenditions;
    }

    @Override
    public URI getRemoteURI(String path) throws URISyntaxException {
        String prefix = StringUtils.removeEnd(getServer(), "/");
        path = StringUtils.replace(StringUtils.removeStart(path, "/"), JcrConstants.JCR_CONTENT, "_jcr_content");
        path = path.replaceAll(" ", "%20");

        return new URI(prefix + "/" + path);
    }

    private void buildRemoteHttpExecutor() {
        URL url;
        try {
            url = new URL(this.server);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Remote server address is malformed");
        }

        if (!url.getProtocol().equalsIgnoreCase("https")) {
            if (this.allowInsecureRemote) {
                log.warn("Remote Assets connection is not HTTPS - authentication username and password will be"
                        + " communicated in CLEAR TEXT.  This configuration is NOT recommended, as it may allow"
                        + " credentials to be compromised!");
            } else {
                throw new IllegalArgumentException("Remote server address must be HTTPS so that credentials"
                        + " cannot be compromised.  As an alternative, you may configure remote assets to allow"
                        + " use of a non-HTTPS connection, allowing connection credentials to potentially be"
                        + " compromised AT YOUR OWN RISK.");
            }
        }

        HttpHost host = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
        this.remoteAssetsHttpExecutor = Executor.newInstance()
                .auth(host, username, password)
                .authPreemptive(host);
    }
}