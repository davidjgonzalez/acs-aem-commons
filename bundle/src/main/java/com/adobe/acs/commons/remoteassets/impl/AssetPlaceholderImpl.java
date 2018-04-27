package com.adobe.acs.commons.remoteassets.impl;

import com.adobe.acs.commons.assets.FileExtensionMimeTypeConstants;
import com.adobe.acs.commons.remoteassets.AssetPlaceholder;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.dam.commons.util.DamUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
@Service
public class AssetPlaceholderImpl implements AssetPlaceholder {
    private static final Logger log = LoggerFactory.getLogger(AssetPlaceholderImpl.class);

    @Reference
    private DynamicClassLoaderManager dynamicClassLoaderManager;

    private static final String ASSET_FILE_PREFIX = "/remoteassets/remote_asset";

    public void setOriginalRenditionPlaceholder(final Resource resource) {

        Asset asset = DamUtil.resolveToAsset(resource);

        if (asset != null && asset.getRendition(DamConstants.ORIGINAL_FILE) != null) {
            log.trace("New base asset note already has an original rendition... skipping");
            return;
        }

        InputStream inputStream = new ByteArrayInputStream(StringUtils.EMPTY.getBytes());

        try {
            final String mimeType = asset.getMimeType();

            if (FileExtensionMimeTypeConstants.EXT_3G2.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".3g2");
            } else if (FileExtensionMimeTypeConstants.EXT_3GP.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".3gp");
            } else if (FileExtensionMimeTypeConstants.EXT_AAC.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".aac");
            } else if (FileExtensionMimeTypeConstants.EXT_AIFF.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".aiff");
            } else if (FileExtensionMimeTypeConstants.EXT_AVI.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".avi");
            } else if (FileExtensionMimeTypeConstants.EXT_BMP.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".bmp");
            } else if (FileExtensionMimeTypeConstants.EXT_CSS.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".css");
            } else if (FileExtensionMimeTypeConstants.EXT_DOC.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".doc");
            } else if (FileExtensionMimeTypeConstants.EXT_DOCX.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".docx");
            } else if (FileExtensionMimeTypeConstants.EXT_AI_EPS_PS.equals(mimeType)) {
                inputStream = getCorrectBinaryTypeStream(resource, "ai", "eps", "ps");
            } else if (FileExtensionMimeTypeConstants.EXT_EPUB.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".epub");
            } else if (FileExtensionMimeTypeConstants.EXT_F4V.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".f4v");
            } else if (FileExtensionMimeTypeConstants.EXT_FLA_SWF.equals(mimeType)) {
                inputStream = getCorrectBinaryTypeStream(resource, "fla", "swf");
            } else if (FileExtensionMimeTypeConstants.EXT_GIF.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".gif");
            } else if (FileExtensionMimeTypeConstants.EXT_HTML.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".html");
            } else if (FileExtensionMimeTypeConstants.EXT_INDD.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".indd");
            } else if (FileExtensionMimeTypeConstants.EXT_JAR.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".jar");
            } else if (FileExtensionMimeTypeConstants.EXT_JPEG_JPG.equals(mimeType)) {
                inputStream = getCorrectBinaryTypeStream(resource, "jpeg", "jpg");
            } else if (FileExtensionMimeTypeConstants.EXT_M4V.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".m4v");
            } else if (FileExtensionMimeTypeConstants.EXT_MIDI.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".midi");
            } else if (FileExtensionMimeTypeConstants.EXT_MOV.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".mov");
            } else if (FileExtensionMimeTypeConstants.EXT_MP3.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".mp3");
            } else if (FileExtensionMimeTypeConstants.EXT_MP4.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".mp4");
            } else if (FileExtensionMimeTypeConstants.EXT_M2V_MPEG_MPG.equals(mimeType)) {
                inputStream = getCorrectBinaryTypeStream(resource, "m2v", "mpeg", "mpg");
            } else if (FileExtensionMimeTypeConstants.EXT_OGG.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".ogg");
            } else if (FileExtensionMimeTypeConstants.EXT_OGV.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".ogv");
            } else if (FileExtensionMimeTypeConstants.EXT_PDF.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".pdf");
            } else if (FileExtensionMimeTypeConstants.EXT_PNG.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".png");
            } else if (FileExtensionMimeTypeConstants.EXT_PPT.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".ppt");
            } else if (FileExtensionMimeTypeConstants.EXT_PPTX.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".pptx");
            } else if (FileExtensionMimeTypeConstants.EXT_PSD.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".psd");
            } else if (FileExtensionMimeTypeConstants.EXT_RAR.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".rar");
            } else if (FileExtensionMimeTypeConstants.EXT_RTF.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".rtf");
            } else if (FileExtensionMimeTypeConstants.EXT_SVG.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".svg");
            } else if (FileExtensionMimeTypeConstants.EXT_TAR.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".tar");
            } else if (FileExtensionMimeTypeConstants.EXT_TIF_TIFF.equals(mimeType)) {
                inputStream = getCorrectBinaryTypeStream(resource, "tif", "tiff");
            } else if (FileExtensionMimeTypeConstants.EXT_TXT.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".txt");
            } else if (FileExtensionMimeTypeConstants.EXT_WAV.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".wav");
            } else if (FileExtensionMimeTypeConstants.EXT_WEBM.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".webm");
            } else if (FileExtensionMimeTypeConstants.EXT_WMA.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".wma");
            } else if (FileExtensionMimeTypeConstants.EXT_WMV.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".wmv");
            } else if (FileExtensionMimeTypeConstants.EXT_XLS.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".xls");
            } else if (FileExtensionMimeTypeConstants.EXT_XLSX.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".xlsx");
            } else if (FileExtensionMimeTypeConstants.EXT_XML.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".xml");
            } else if (FileExtensionMimeTypeConstants.EXT_ZIP.equals(mimeType)) {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".zip");
            } else {
                inputStream = this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(ASSET_FILE_PREFIX + ".jpeg");
            }

            log.trace("Set original rendition to placeholder image for [ {} ]", asset.getPath());

            asset.addRendition(DamConstants.ORIGINAL_FILE, inputStream, mimeType);

        } catch (RepositoryException e) {
            log.error("Could not add placeholder original rendition for [ {} ]", resource.getPath(), e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ie) {
                log.error("Could not close inputstream for placeholder original rendition for [ {} ]", resource.getPath(), ie);
            }
        }
    }


    private InputStream getCorrectBinaryTypeStream(final Resource fileResource, String... files) throws RepositoryException {
        Resource renditionResource = fileResource.getParent();
        Asset assetResource = DamUtil.resolveToAsset(renditionResource);

        String remoteAssetFileUri = ASSET_FILE_PREFIX + "." + files[0];
        String assetFileExtension = FilenameUtils.getExtension(assetResource.getName());
        String renditionParentFileExtension = FilenameUtils.getExtension(renditionResource.getName());
        for (String file : files) {
            if (DamConstants.ORIGINAL_FILE.equals(renditionResource.getName()) && file.equals(assetFileExtension)
                    || !DamConstants.ORIGINAL_FILE.equals(renditionResource.getName()) && file.equals(renditionParentFileExtension)) {

                remoteAssetFileUri = ASSET_FILE_PREFIX + "." + file;
                break;
            }
        }

        return this.dynamicClassLoaderManager.getDynamicClassLoader().getResourceAsStream(remoteAssetFileUri);
    }

}
