/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2013 Adobe
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

package com.adobe.acs.commons.content.properties.bulk.impl.servlets;


import com.adobe.acs.commons.content.properties.bulk.impl.Status;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@SlingServlet(
        label = "ACS AEM Commons - Property Manager - Move Property Servlet",
        description = "...",
        methods = "POST",
        resourceTypes = "acs-commons/components/utilities/bulk-property-manager",
        selectors = MovePropertyServlet.TYPE,
        extensions = "json"
)
public class MovePropertyServlet extends AbstractBaseServlet {
    public static final String TYPE = "move";

    private static final Logger log = LoggerFactory.getLogger(MovePropertyServlet.class);

    @Override
    Map<String, Object> getParams(JSONObject json) throws JSONException {
        final Map<String, Object> map = new HashMap<String, Object>();

        json = json.getJSONObject(TYPE);

        map.put("src", json.getString("src"));
        map.put("dest", json.getString("dest"));

        return map;
    }

    @Override
    Status execute(final Resource resource, final ValueMap params) {
        final String srcPropertyName = params.get("src", String.class);
        final String destPropertyName = params.get("dest", String.class);

        final ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);

        if (mvm.keySet().contains(srcPropertyName)) {
            mvm.put(destPropertyName, mvm.get(srcPropertyName));
            mvm.remove(srcPropertyName);

            return Status.SUCCESS;
        } else {
            return Status.NOOP;
        }
    }
}