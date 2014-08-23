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

package com.adobe.acs.commons.content.properties.bulk.impl;


import com.day.cq.commons.jcr.JcrUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.Map;

@SlingServlet(
        label = "ACS AEM Commons - Property Manager - Remove Property Servlet",
        description = "...",
        methods = "POST",
        resourceTypes = "acs-commons/components/utilities/bulk-property-manager",
        selectors = RemovePropertyServlet.TYPE,
        extensions = "json"
)
public class RemovePropertyServlet extends AbstractBaseServlet {
    private static final Logger log = LoggerFactory.getLogger(RemovePropertyServlet.class);
    public static final String TYPE = "remove";

    @Override
    Map<String, Object> getParams(JSONObject json) throws JSONException {
        final Map<String, Object> map = new HashMap<String, Object>();

        json = json.getJSONObject(TYPE);

        map.put("name", json.getString("name"));

        return map;
    }

    @Override
    boolean execute(final Resource resource, final ValueMap params) {
        final Node node = resource.adaptTo(Node.class);
        final ModifiableValueMap properties = resource.adaptTo(ModifiableValueMap.class);

        final String propertyName = params.get("name", "");
        try {
            if(StringUtils.isNotBlank(propertyName) && node.hasProperty(propertyName)) {
                JcrUtil.setProperty(node, propertyName, null);
                return true;
            }
        } catch (RepositoryException e) {
            log.warn("Could not properly process resource [ {} ]", resource.getPath());
        }

        return false;
    }
}