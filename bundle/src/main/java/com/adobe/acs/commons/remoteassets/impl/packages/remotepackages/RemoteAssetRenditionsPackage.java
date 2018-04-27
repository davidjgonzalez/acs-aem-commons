package com.adobe.acs.commons.remoteassets.impl.packages.remotepackages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class RemoteAssetRenditionsPackage extends RemotePackage {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetRenditionsPackage.class);

    private final List<String> excludedRenditions;

    public RemoteAssetRenditionsPackage(List<String> syncPaths, List<String> excludedRenditions) {
        super("remote-asset-renditions__" + UUID.randomUUID().toString(), PACKAGE_GROUP, PACKAGE_VERSION, syncPaths);
        this.excludedRenditions = excludedRenditions;
    }

    public String getFilter() {
        final JsonArray json = new JsonArray();

        for(String syncPath : syncPaths) {

            JsonObject filter = new JsonObject();
            filter.add("root", new JsonPrimitive(syncPath + "/jcr:content/renditions"));

            JsonArray rules = new JsonArray();

            for (String rendition : excludedRenditions) {
                JsonObject excludeRenditions = new JsonObject();
                excludeRenditions.add("modifier", new JsonPrimitive("exclude"));
                excludeRenditions.add("pattern", new JsonPrimitive(syncPath + "/jcr:content/renditions/" + rendition));
                rules.add(excludeRenditions);
            }

            filter.add("rules", rules);

            json.add(filter);
        }

        log.debug("RENDITION JSON: {}", json.toString());
        return json.toString();
    }
}

