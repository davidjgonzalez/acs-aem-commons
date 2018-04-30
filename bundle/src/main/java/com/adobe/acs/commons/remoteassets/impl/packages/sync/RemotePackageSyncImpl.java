package com.adobe.acs.commons.remoteassets.impl.packages.sync;

import com.adobe.acs.commons.fam.ActionManager;
import com.adobe.acs.commons.fam.ActionManagerFactory;
import com.adobe.acs.commons.remoteassets.*;
import com.adobe.acs.commons.remoteassets.impl.RemoteAssets;
import com.adobe.acs.commons.remoteassets.impl.RemoteAssetsSyncException;
import com.adobe.acs.commons.remoteassets.impl.packages.listeners.InstalledAssetListener;
import com.adobe.acs.commons.remoteassets.impl.packages.listeners.InstalledAssetRenditionListener;
import com.adobe.acs.commons.remoteassets.impl.packages.listeners.InstalledTagListener;
import com.adobe.acs.commons.remoteassets.impl.packages.listeners.PathsTrackerListener;
import com.adobe.acs.commons.remoteassets.impl.packages.remotepackages.RemoteAssetRenditionsPackage;
import com.adobe.acs.commons.remoteassets.impl.packages.remotepackages.RemoteAssetsPackage;
import com.adobe.acs.commons.remoteassets.impl.packages.remotepackages.RemotePackage;
import com.adobe.acs.commons.remoteassets.impl.packages.remotepackages.RemoteTagsPackage;
import com.day.cq.dam.commons.util.DamUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.Packaging;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Service
public class RemotePackageSyncImpl implements RemoteAssetsSync, RemoteAssetsRenditionsSync, RemoteTagsSync {
    private static final Logger log = LoggerFactory.getLogger(RemotePackageSyncImpl.class);

    private static final String ACTION_MANAGER_NAME = "ACS AEM Commons - AEM Asset Sync";

    @Reference
    private Packaging packaging;

    @Reference
    private RemoteAssetsConfig config;

    @Reference
    private HttpClientBuilderFactory httpClientBuilderFactory;

    @Reference
    private RemoteAssetsRenditions remoteAssetsRenditions;


    @Reference
    private SyncStateManager syncStateManager;

    @Reference
    private ActionManagerFactory actionManagerFactory;

    @Override
    public long syncAssets(final ResourceResolver resourceResolver) {
        final RemotePackage remotePackage = new RemoteAssetsPackage(config.getDamSyncPaths(), config.getEagerAssetRenditions());

        if (remotePackage.isEmpty()) {
            log.info("No DAM Sync paths have been configured... Skipping.");
            return 0;
        }

        final ImportOptions importOptions = new ImportOptions();
        importOptions.setImportMode(ImportMode.MERGE);
        importOptions.setAutoSaveThreshold(config.getSaveInterval());
        importOptions.setNonRecursive(true);
        importOptions.setListener(new InstalledAssetListener(resourceResolver));

        try {
            createRemotePackage(remotePackage);
            configureRemotePackage(remotePackage);
            buildRemotePackage(remotePackage);
            final List<String> paths = installLocalPackage(resourceResolver, importOptions, downloadRemotePackage(remotePackage));
            removeRemotePackage(remotePackage);
            addPlaceholderRenditions(resourceResolver, paths);

            return paths.size();
        } catch (RemoteAssetsSyncException | URISyntaxException e) {
            log.error("Error syncing remote asset nodes.", e);
            return -1;
        }
    }

    @Override
    public long syncTags(final ResourceResolver resourceResolver) {
        final RemotePackage remotePackage = new RemoteTagsPackage(config.getTagSyncPaths());

        if (remotePackage.isEmpty()) {
            log.info("No Tag Sync paths have been configured... Skipping.");
            return 0;
        }

        final ImportOptions importOptions = new ImportOptions();
        importOptions.setImportMode(ImportMode.MERGE);
        importOptions.setAutoSaveThreshold(config.getSaveInterval());
        importOptions.setNonRecursive(true);
        importOptions.setListener(new InstalledTagListener());

        try {
            createRemotePackage(remotePackage);
            configureRemotePackage(remotePackage);
            buildRemotePackage(remotePackage);
            final List<String> paths = installLocalPackage(resourceResolver, importOptions, downloadRemotePackage(remotePackage));
            removeRemotePackage(remotePackage);

            return paths.size();
        } catch (RemoteAssetsSyncException | URISyntaxException e) {
            log.error("Error syncing remote tag nodes.", e);
            return -1;
        }
    }

    @Override
    public void syncAssetRenditions(final ResourceResolver resourceResolver, final String... assetPaths) {
        final Map<String, Collection<String>> assetsAndExcludedRenditions = new HashMap<>();
        Arrays.stream(assetPaths).forEach(p -> assetsAndExcludedRenditions.put(p, Collections.EMPTY_LIST));

        syncAssetRenditions(resourceResolver, assetsAndExcludedRenditions);
    }

    @Override
    public void syncAssetRenditions(final ResourceResolver resourceResolver, Map<String, Collection<String>> assetAndExcludedRenditions) {
        if (assetAndExcludedRenditions == null || assetAndExcludedRenditions.isEmpty()) {
            return;
        }

        final ImportOptions importOptions = new ImportOptions();
        importOptions.setImportMode(ImportMode.UPDATE);
        importOptions.setAutoSaveThreshold(config.getSaveInterval());
        importOptions.setNonRecursive(true);
        importOptions.setListener(new InstalledAssetRenditionListener());

        ActionManager actionManager = null;

        try {
            actionManager = config.getActionManager();

            if (actionManager != null) {
                actionManager.deferredWithResolver(rr -> {
                    config.applyEventUserData(rr);

                    syncAssetRenditionsWorker(rr, importOptions, assetAndExcludedRenditions);
                });
            }
        } catch (LoginException e) {
            log.error("Could not create Action Manager. Using synchronous syncing.", e);
        }

        if (actionManager == null) {
            syncAssetRenditionsWorker(resourceResolver, importOptions, assetAndExcludedRenditions);

            try {
                resourceResolver.commit();
            } catch (PersistenceException e) {
                log.error("Could not save asset renditions sync changes for [ {} ] assets", assetAndExcludedRenditions.keySet().size());
            }
        }
    }

    protected void syncAssetRenditionsWorker(final ResourceResolver resourceResolver, ImportOptions importOptions, final Map<String, Collection<String>> assetsAndExcludedRenditions) {
        final RemotePackage remotePackage = new RemoteAssetRenditionsPackage(assetsAndExcludedRenditions, config.getEagerAssetRenditions());

        try {
            assetsAndExcludedRenditions.keySet().stream()
                    .map(resourceResolver::getResource)
                    .map(DamUtil::resolveToAsset)
                    .forEach(asset -> {
                        remoteAssetsRenditions.addSyncingRendition(asset);
                    });

            resourceResolver.commit();

            createRemotePackage(remotePackage);
            configureRemotePackage(remotePackage);
            buildRemotePackage(remotePackage);
            installLocalPackage(resourceResolver, importOptions, downloadRemotePackage(remotePackage));
            removeRemotePackage(remotePackage);

            resourceResolver.commit();

            if (importOptions.getListener() instanceof PathsTrackerListener) {
                ((PathsTrackerListener) importOptions.getListener()).getPaths()
                        .stream()
                        .map(path -> resourceResolver.getResource(path))
                        .forEach(r -> {
                            RemoteAssets.setIsRemoteAsset(r, false);
                            RemoteAssets.setIsRemoteSyncFailed(r, null);
                            remoteAssetsRenditions.removeSyncingRendition(DamUtil.resolveToAsset(r));
                            syncStateManager.remove(r.getPath());
                        });
            }

        } catch (RemoteAssetsSyncException | PersistenceException | URISyntaxException e) {
            log.error("Error syncing remote asset renditions via package.", e);
        }
    }

    private void createRemotePackage(final RemotePackage remotePackage) throws RemoteAssetsSyncException, URISyntaxException {
        final Executor executor = config.getRemoteAssetsHttpExecutor();
        final Request request = Request.Post(config.getRemoteURI("/crx/packmgr/service/exec.json"));

        final List<NameValuePair> params = new ArrayList<>();

        params.add(new BasicNameValuePair("cmd", "create"));
        params.add(new BasicNameValuePair("packageName", remotePackage.getName()));
        params.add(new BasicNameValuePair("groupName", remotePackage.getGroup()));
        params.add(new BasicNameValuePair("packageVersion", remotePackage.getVersion()));

        request.bodyForm(params);

        try {
            final HttpResponse response = executor.execute(request).returnResponse();
            if (response.getStatusLine().getStatusCode() == 200) {
                JsonObject jsonResponse = new Gson().fromJson(IOUtils.toString(response.getEntity().getContent()), JsonObject.class);
                if (jsonResponse.get("success").getAsBoolean()) {
                    String packagePath = jsonResponse.get("path").getAsString();
                    log.debug("Created a remote package on [ {}{} ]", config.getServer(), packagePath);
                    remotePackage.setPath(packagePath);
                } else {
                    throw new RemoteAssetsSyncException(String.format("Could not create a remote package on [ %s ] because:\n %s", config.getServer(), jsonResponse.get("msg").getAsString()));
                }
            } else {
                throw new RemoteAssetsSyncException(String.format("Could not create a remote package on [ %s ] because:\n %s", config.getServer(), IOUtils.toString(response.getEntity().getContent())));
            }
        } catch (IOException e) {
            throw new RemoteAssetsSyncException(e);
        }
    }

    private void configureRemotePackage(final RemotePackage remotePackage) throws RemoteAssetsSyncException, URISyntaxException {
        final Executor executor = config.getRemoteAssetsHttpExecutor();
        final Request request = Request.Post(config.getRemoteURI("/crx/packmgr/update.jsp"));

        final HttpEntity params = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addTextBody("path", remotePackage.getPath(), ContentType.DEFAULT_TEXT)
                .addTextBody("packageName", remotePackage.getName(), ContentType.DEFAULT_TEXT)
                .addTextBody("packageGroup", remotePackage.getGroup(), ContentType.DEFAULT_TEXT)
                .addTextBody("description", remotePackage.getDescription(), ContentType.DEFAULT_TEXT)
                .addTextBody("filter", remotePackage.getFilter(), ContentType.DEFAULT_TEXT)
                .build();

        request.body(params);

        try {
            final HttpResponse response = executor.execute(request).returnResponse();
            if (response.getStatusLine().getStatusCode() == 200) {

                JsonObject jsonResponse = new Gson().fromJson(IOUtils.toString(response.getEntity().getContent()), JsonObject.class);
                if (jsonResponse.get("success").getAsBoolean()) {
                    log.debug("Configured the remote package at [ {}{} ]", config.getServer(), remotePackage.getPath());
                } else {
                    throw new RemoteAssetsSyncException(String.format("Could not configure a remote package on [ %s ] because:\n %s", config.getServer(), jsonResponse.get("msg").getAsString()));
                }
            } else {
                throw new RemoteAssetsSyncException(String.format("Could not configure the remote package on [ %s ] because:\n %s", config.getServer(), IOUtils.toString(response.getEntity().getContent())));
            }
        } catch (IOException e) {
            throw new RemoteAssetsSyncException(e);
        }
    }

    private void buildRemotePackage(RemotePackage remotePackage) throws RemoteAssetsSyncException, URISyntaxException {
        final Executor executor = config.getRemoteAssetsHttpExecutor();
        final Request request = Request.Post(config.getRemoteURI("/crx/packmgr/service.jsp"));

        final HttpEntity params = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addTextBody("cmd", "build", ContentType.DEFAULT_TEXT)
                .addTextBody("name", remotePackage.getName(), ContentType.DEFAULT_TEXT)
                .addTextBody("group", remotePackage.getGroup(), ContentType.DEFAULT_TEXT)
                .build();

        request.body(params);

        try {
            if (executor.execute(request).returnResponse().getStatusLine().getStatusCode() != 200) {
                throw new RemoteAssetsSyncException(String.format("Could not build a remote package at [ %s ]", request.toString()));
            }
            log.debug("Built the remote package on [ {}{} ]", config.getServer(), remotePackage.getPath());
        } catch (IOException e) {
            throw new RemoteAssetsSyncException(e);
        }
    }

    private InputStream downloadRemotePackage(RemotePackage remotePackage) throws RemoteAssetsSyncException, URISyntaxException {
        final Executor executor = config.getRemoteAssetsHttpExecutor();
        final Request request = Request.Post(config.getRemoteURI("/crx/packmgr/service.jsp"));

        final HttpEntity params = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addTextBody("cmd", "get", ContentType.DEFAULT_TEXT)
                .addTextBody("name", remotePackage.getName(), ContentType.DEFAULT_TEXT)
                .addTextBody("group", remotePackage.getGroup(), ContentType.DEFAULT_TEXT)
                .build();

        request.body(params);

        try {
            final HttpResponse response = executor.execute(request).returnResponse();
            if (response.getStatusLine().getStatusCode() == 200) {
                final InputStream tmp = response.getEntity().getContent();
                log.debug("Downloaded the remote package on [ {}{} ]", config.getServer(), remotePackage.getPath());
                return tmp;
            } else {
                throw new RemoteAssetsSyncException(String.format("Could not build a remote package at [ %s ]", request.toString()));
            }
        } catch (IOException e) {
            throw new RemoteAssetsSyncException(e);
        }
    }

    private List<String> installLocalPackage(ResourceResolver resourceResolver, ImportOptions importOptions, InputStream is) throws RemoteAssetsSyncException {
        try {
            final JcrPackageManager packageManager = packaging.getPackageManager(resourceResolver.adaptTo(Session.class));
            final JcrPackage jcrPackage = packageManager.upload(is, true);

            jcrPackage.install(importOptions);

            log.debug("Installed the remote package [ {} ] locally.", jcrPackage.getDefinition().getId().getName());

            packageManager.remove(jcrPackage);

            if (importOptions.getListener() instanceof PathsTrackerListener) {
                return ((PathsTrackerListener) importOptions.getListener()).getPaths();
            } else {
                return Collections.EMPTY_LIST;
            }
        } catch(Exception e) {
            throw new RemoteAssetsSyncException(e);
        }
    }


    private void removeRemotePackage(final RemotePackage remotePackage) throws RemoteAssetsSyncException, URISyntaxException {
        final Executor executor = config.getRemoteAssetsHttpExecutor();
        final Request request = Request.Post(config.getRemoteURI("/crx/packmgr/service.jsp"));

        final HttpEntity params = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addTextBody("cmd", "rm", ContentType.DEFAULT_TEXT)
                .addTextBody("name", remotePackage.getName(), ContentType.DEFAULT_TEXT)
                .addTextBody("group", remotePackage.getGroup(), ContentType.DEFAULT_TEXT)
                .build();

        try {
            if (executor.execute(request).returnResponse().getStatusLine().getStatusCode() != 200) {
                throw new RemoteAssetsSyncException(String.format("Could not build a remote package at [ %s ]", request.toString()));
            }
            log.debug("Cleaned-up the remote package on [ {}{} ]", config.getServer(), remotePackage.getPath());
        } catch (IOException e) {
            throw new RemoteAssetsSyncException(e);
        }
    }


    private void addPlaceholderRenditions(final ResourceResolver resourceResolver, final List<String> paths) {
        AtomicInteger count = new AtomicInteger(0);

        paths.stream()
                .map(resourceResolver::getResource)
                .filter(Objects::nonNull)
                .map(r -> r.getChild(JcrConstants.JCR_CONTENT))
                .filter(Objects::nonNull)
                .forEach(assetJcrContentResource -> {
                    try {
                        RemoteAssets.setIsRemoteAsset(assetJcrContentResource, true);

                        remoteAssetsRenditions.setPlaceholderRenditions(DamUtil.resolveToAsset(assetJcrContentResource));

                        if (count.incrementAndGet() % config.getSaveInterval() == 0) {
                            resourceResolver.commit();
                        }
                    } catch (PersistenceException e) {
                        log.error("Could not save Is Remote Asset Status to sync'd assets.", e);
                    }
                });

        if (resourceResolver.hasChanges()) {
            try {
                resourceResolver.commit();
            } catch (PersistenceException e) {
                log.error("Could not save Is Remote Asset Status to sync'd assets.", e);
            }
        }
    }
}