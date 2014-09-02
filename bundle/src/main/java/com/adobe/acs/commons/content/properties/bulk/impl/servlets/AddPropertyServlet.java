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
import com.adobe.acs.commons.util.TypeUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@SlingServlet(
        label = "ACS AEM Commons - Property Manager - Add Property Servlet",
        description = "...",
        methods = "POST",
        resourceTypes = "acs-commons/components/utilities/bulk-property-manager",
        selectors = AddPropertyServlet.TYPE,
        extensions = "json"
)
public class AddPropertyServlet extends AbstractBaseServlet {
    public static final String TYPE = "add";

    private static final Logger log = LoggerFactory.getLogger(AddPropertyServlet.class);

    @Override
    Map<String, Object> getParams(JSONObject json) throws JSONException {
        final Map<String, Object> map = new HashMap<String, Object>();

        json = json.getJSONObject(AddPropertyServlet.TYPE);

        map.put("name", json.getString("name"));
        map.put("value", json.getString("value"));
        map.put("overwrite", json.optBoolean("overwrite", false));

        final String type = json.optString("type", "String");
        final String value = json.getString("value");

        map.put("value", this.getValueObject(type, value));

        return map;
    }

    @Override
    Status execute(final Resource resource, final ValueMap params) {
        final String propertyName = params.get("name", String.class);
        final boolean overwrite = params.get("overwrite", false);

        final ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);

        if (StringUtils.isNotBlank(propertyName)
                && (!mvm.keySet().contains(propertyName) || overwrite)) {

            mvm.put(propertyName, params.get("value"));

            return Status.SUCCESS;
        } else {
            return Status.NOOP;
        }
    }

    protected final Object getValueObject(final String type, final String value) {
        if(StringUtils.equalsIgnoreCase(type, "Long")) {
            return TypeUtil.toObjectType(value, Long.class);
        } else if(StringUtils.equalsIgnoreCase(type, "Date")) {
            return TypeUtil.toObjectType(value, Date.class);
        } else if(StringUtils.equalsIgnoreCase(type, "Boolean")) {
            return TypeUtil.toObjectType(value, Boolean.class);
        } else {
            return TypeUtil.toObjectType(value, String.class);
        }
    }
}