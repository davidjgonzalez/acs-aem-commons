package com.adobe.acs.commons.images.screenshots;

import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;

public interface Screenshot {

    public enum Type { PNG, JPEG, GIF, PDF };

    InputStream takeScreenshot(ResourceResolver resourceResolver, String scriptResourcePath, Type type,
                               String...  params) throws IOException, RepositoryException, InterruptedException;
}
