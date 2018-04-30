package com.adobe.acs.commons.remoteassets.impl.packages.hooks;

import com.adobe.acs.commons.remoteassets.RemoteAssetsConfig;
import com.adobe.acs.commons.remoteassets.RemoteAssetsRenditions;
import com.adobe.acs.commons.remoteassets.RemoteAssetsRenditionsSync;
import com.adobe.acs.commons.remoteassets.SyncStateManager;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.commons.util.DamUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Properties;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.tika.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;

@Component
@Properties({
        @Property(
                name = "sling.servlet.resourceTypes",
                value = {RemoteAssetsRenditionServlet.RESOURCE_TYPE}
        )
})
@Service
public class RemoteAssetsRenditionServlet extends SlingSafeMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetsRenditionServlet.class);

    private static final String UNKNOWN_EXTENSION = "unknown";
    private static final String PLACEHOLDER_PATH = "/apps/acs-commons/dam/remote-assets/remote_asset.";
    public static final String RESOURCE_TYPE = "acs-commons/dam/remote-assets/remote-asset-rendition";

    @Reference
    private RemoteAssetsRenditionsSync remoteAssetsRenditionsSync;

    @Reference
    private SyncStateManager syncStateManager;

    @Reference
    private MimeTypeService mimeTypeService;

    @Reference
    private RemoteAssetsConfig config;

    @Reference
    private RemoteAssetsRenditions remoteAssetsRenditions;

    @Override
    protected final void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
            ServletException, IOException {


        ResourceResolver serviceResourceResolver = null;

        try {
            serviceResourceResolver = config.getResourceResolver();

            final Resource requestResource = serviceResourceResolver.getResource(request.getResource().getPath());

            if (requestResource != null) {
                final Asset asset = DamUtil.resolveToAsset(requestResource);

                if (asset == null) {
                    response.sendError(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }

                if (!syncStateManager.contains(asset.getPath())) {
                        syncStateManager.add(asset.getPath());
                        doGetRemoteRenditions(serviceResourceResolver, request, response, asset);
                } else {
                    doGetPlaceholder(serviceResourceResolver, request, response, asset);
                }
            }
        } catch (URISyntaxException e) {
            throw new ServletException(e);
        } finally {
            if (serviceResourceResolver != null) {
                if (serviceResourceResolver.hasChanges()) {
                    serviceResourceResolver.commit();
                }

                config.closeResourceResolver(serviceResourceResolver);
            }
        }
    }

    private void doGetPlaceholder(ResourceResolver serviceResourceResolver, SlingHttpServletRequest request, SlingHttpServletResponse response, Asset asset) throws
            ServletException, IOException {

        final String extension = mimeTypeService.getExtension(asset.getMimeType());

        String placeholderPath = PLACEHOLDER_PATH + UNKNOWN_EXTENSION;

        if (!StringUtils.isBlank(extension) && serviceResourceResolver.getResource(PLACEHOLDER_PATH + extension) != null) {
            placeholderPath = PLACEHOLDER_PATH + extension;
        }

        request.getRequestDispatcher(placeholderPath).forward(request, response);
    }

    private void doGetRemoteRenditions(ResourceResolver serviceResourceResolver, SlingHttpServletRequest request, SlingHttpServletResponse response, Asset asset) throws
            IOException, URISyntaxException {
        // Figure out which rendition is request
        final List<String> excludeRenditionNames = new ArrayList<>(config.getEagerAssetRenditions());
        final Resource renditionResource = serviceResourceResolver.getResource(request.getResource().getPath());
        final String renditionName = renditionResource.getName();

        final Executor executor = config.getRemoteAssetsHttpExecutor();
        final Request remoteRequest = Request.Get(config.getRemoteURI(renditionResource.getPath()));

        final HttpResponse remoteResponse = executor.execute(remoteRequest).returnResponse();
        if (remoteResponse.getStatusLine().getStatusCode() == 200) {

            if (remoteResponse.getEntity().getContentEncoding() != null) {
                response.setCharacterEncoding(remoteResponse.getEntity().getContentEncoding().getValue());
            }

            if (remoteResponse.getEntity().getContentType() != null) {
                response.setContentType(remoteResponse.getEntity().getContentType().getValue());
            }

            response.setHeader("Content-Length", String.valueOf(remoteResponse.getEntity().getContentLength()));

            try (InputStream in = remoteResponse.getEntity().getContent()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(in, baos);
                byte[] bytes = baos.toByteArray();


                IOUtils.copy(new ByteArrayInputStream(bytes), response.getOutputStream());

                serviceResourceResolver.delete(renditionResource);
                asset.addRendition(renditionName, new ByteArrayInputStream(bytes), remoteResponse.getEntity().getContentType().getValue());
                excludeRenditionNames.add(renditionName);
            }
        }

        final Map<String, Collection<String>> filters = new HashMap<>();
        filters.put(asset.getPath(), excludeRenditionNames);

        remoteAssetsRenditionsSync.syncAssetRenditions(serviceResourceResolver, filters);
    }


}
