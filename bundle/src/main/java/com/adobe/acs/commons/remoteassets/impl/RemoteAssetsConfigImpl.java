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
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final Logger LOG = LoggerFactory.getLogger(RemoteAssetsConfigImpl.class);
    private static final boolean DEFAULT_ALLOW_INSECURE = false;

    @Property(label = "Server")
    private static final String SERVER_PROP = "server.url";

    @Property(label = "Username")
    private static final String USERNAME_PROP = "server.user";

    @Property(label = "Password")
    private static final String PASSWORD_PROP = "server.pass";

    @Property(label = "Allow Insecure Connection", description = "Allow non-https connection to remote assets server, "
            + "allowing potential compromize of conenction credentials", boolValue = DEFAULT_ALLOW_INSECURE)
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
    private Integer retryDelay;
    private Integer saveInterval;
    private String eventUserData = StringUtils.EMPTY;
    private Set<String> whitelistedServiceUsers = new HashSet<>();

    private Executor remoteAssetsHttpExecutor;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    /**
     * Method to run on activation.
     * @param componentContext ComponentContext
     */
    @Activate
    @Modified
    private void activate(final ComponentContext componentContext) {
        final Dictionary<String, Object> properties = componentContext.getProperties();

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
        this.retryDelay = PropertiesUtil.toInteger(properties.get(RETRY_DELAY_PROP), 1);
        this.saveInterval = PropertiesUtil.toInteger(properties.get(SAVE_INTERVAL_PROP), 100);
        this.eventUserData = PropertiesUtil.toString(properties.get(EVENT_USER_DATA_PROP), StringUtils.EMPTY);
        this.whitelistedServiceUsers = Stream.of(PropertiesUtil.toStringArray(properties.get(WHITELISTED_SERVICE_USERS_PROP), new String[0]))
                .filter(item -> StringUtils.isNotBlank(item))
                .collect(Collectors.toSet());

        buildRemoteHttpExecutor();
    }

    /**
     * @see RemoteAssetsConfig#getServer()
     * @return String
     */
    @Override
    public String getServer() {
        return this.server;
    }

    /**
     * @see RemoteAssetsConfig#getUsername()
     * @return String
     */
    @Override
    public String getUsername() {
        return this.username;
    }

    /**
     * @see RemoteAssetsConfig#getPassword()
     * @return String
     */
    @Override
    public String getPassword() {
        return this.password;
    }

    /**
     * @see RemoteAssetsConfig#getTagSyncPaths()
     * @return List<String>
     */
    @Override
    public List<String> getTagSyncPaths() {
        return this.tagSyncPaths;
    }

    /**
     * @see RemoteAssetsConfig#getDamSyncPaths()
     * @return List<String>
     */
    @Override
    public List<String> getDamSyncPaths() {
        return this.damSyncPaths;
    }

    /**
     * @see RemoteAssetsConfig#getRetryDelay()
     * @return Integer
     */
    @Override
    public Integer getRetryDelay() {
        return this.retryDelay;
    }

    /**
     * @see RemoteAssetsConfig#getSaveInterval()
     * @return Integer
     */
    @Override
    public Integer getSaveInterval() {
        return this.saveInterval;
    }

    /**
     * @see RemoteAssetsConfig#getEventUserData()
     * @return String
     */
    @Override
    public String getEventUserData() {
        return this.eventUserData;
    }

    /**
     * @see RemoteAssetsConfig#getWhitelistedServiceUsers()
     * @return String
     */
    @Override
    public Set<String> getWhitelistedServiceUsers() {
        return this.whitelistedServiceUsers;
    }

    /**
     * @see RemoteAssetsConfig#getRemoteAssetsHttpExecutor()
     * @return Executor
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
            Session session = resourceResolver.adaptTo(Session.class);
            if (StringUtils.isNotBlank(this.getEventUserData())) {
                session.getWorkspace().getObservationManager().setUserData(this.getEventUserData());
            }
            return resourceResolver;
        } catch (Exception e) {
            LOG.error("Remote assets functionality cannot be enabled - service user login failed");
            throw new RemoteAssetsServiceException(e);
        }
    }

    /**
     * @see RemoteAssetsConfig#closeResourceResolver(ResourceResolver)
     */
    @Override
    public void closeResourceResolver(ResourceResolver resourceResolver) {
        if (resourceResolver != null) {
            try {
                Session session = resourceResolver.adaptTo(Session.class);
                if (session != null) {
                    session.logout();
                }
            } catch (Exception e) {
                LOG.warn("Failed session.logout()", e);
            }
            try {
                resourceResolver.close();
            } catch (Exception e) {
                LOG.warn("Failed resourceResolver.close()", e);
            }
        }
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
                LOG.warn("Remote Assets connection is not HTTPS - authentication username and password will be"
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