package com.adobe.acs.commons.remoteassets.impl.packages.remotepackages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.UUID;

public class RemoteTagsPackage extends RemotePackage {
    public RemoteTagsPackage(List<String> syncPaths) {
        super("remote-tags__" + UUID.randomUUID().toString(), PACKAGE_GROUP, PACKAGE_VERSION, syncPaths);
    }

    public String getFilter() {
        final JsonArray json = new JsonArray();


        for(String syncPath : syncPaths) {

            JsonObject filter = new JsonObject();
            filter.add("root", new JsonPrimitive(syncPath));

            JsonArray rules = new JsonArray();
            filter.add("rules", rules);

            json.add(filter);
        }

        return json.toString();
    }
}