package com.adobe.acs.commons.remoteassets.impl.packages.remotepackages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RemoteAssetRenditionsPackage extends RemotePackage {
    private static final Logger log = LoggerFactory.getLogger(RemoteAssetRenditionsPackage.class);

    private final Map<String, Collection<String>> filters;

    public RemoteAssetRenditionsPackage(Map<String, Collection<String>> filters, List<String> extraExcludedRenditions) {
        super("remote-asset-renditions__" + UUID.randomUUID().toString(), PACKAGE_GROUP, PACKAGE_VERSION, filters.keySet());
        this.filters = filters;

        this.filters.entrySet().stream().forEach(e -> {
            e.getValue().addAll(extraExcludedRenditions);
        });
    }

    public String getFilter() {
        final JsonArray json = new JsonArray();

        for(Map.Entry<String, Collection<String>> entry : filters.entrySet()) {

            JsonObject filter = new JsonObject();
            filter.add("root", new JsonPrimitive(entry.getKey() + "/jcr:content/renditions"));

            JsonArray rules = new JsonArray();

            for (String rendition : entry.getValue()) {
                JsonObject excludeRenditions = new JsonObject();
                excludeRenditions.add("modifier", new JsonPrimitive("exclude"));
                excludeRenditions.add("pattern", new JsonPrimitive(entry.getKey() + "/jcr:content/renditions/" + rendition));
                rules.add(excludeRenditions);
            }

            filter.add("rules", rules);

            json.add(filter);
        }

        log.debug("RENDITION JSON: {}", json.toString());
        return json.toString();
    }
}

