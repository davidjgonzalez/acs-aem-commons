package com.adobe.acs.commons.remoteassets.impl.packages.remotepackages;

import com.adobe.acs.commons.remoteassets.impl.RemoteAssets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.UUID;

public class RemoteAssetsPackage extends RemotePackage {
    private final List<String> includedRenditions;

    public RemoteAssetsPackage(List<String> syncPaths, List<String> includedRenditions) {
        super("remote-assets__" + UUID.randomUUID().toString(), PACKAGE_GROUP, PACKAGE_VERSION, syncPaths);
        this.includedRenditions = includedRenditions;
    }

    public String getFilter() {
        final JsonArray json = new JsonArray();

        for(String syncPath : syncPaths) {

            JsonObject filter = new JsonObject();
            filter.add("root", new JsonPrimitive(syncPath));

            JsonArray rules = new JsonArray();

            JsonObject excludeRenditions = new JsonObject();
            excludeRenditions.add("modifier", new JsonPrimitive("exclude"));
            excludeRenditions.add("pattern", new JsonPrimitive("/content/dam/.*/renditions/.*"));
            rules.add(excludeRenditions);

            JsonObject excludeRemoteProperties = new JsonObject();
            excludeRemoteProperties.add("modifier", new JsonPrimitive("exclude"));
            excludeRemoteProperties.add("pattern", new JsonPrimitive("/content/dam/.*/jcr:content/" + RemoteAssets.NN_REMOTE));
            rules.add(excludeRemoteProperties);

            for (String rendition : includedRenditions) {
                JsonObject includeRenditions = new JsonObject();
                includeRenditions.add("modifier", new JsonPrimitive("include"));
                includeRenditions.add("pattern", new JsonPrimitive("/content/dam/.*/renditions/" + rendition));
                rules.add(includeRenditions);
            }

            JsonObject excludeSubassets = new JsonObject();
            excludeSubassets.add("modifier", new JsonPrimitive("exclude"));
            excludeSubassets.add("pattern", new JsonPrimitive("/content/dam/.*/subassets/.*"));
            rules.add(excludeSubassets);


            filter.add("rules", rules);

            json.add(filter);
        }

        return json.toString();
    }
}
