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
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.Map;

@SlingServlet(
        label = "ACS AEM Commons - Property Manager - Copy Property Servlet",
        description = "...",
        methods = "POST",
        resourceTypes = "acs-commons/components/utilities/bulk-property-manager",
        selectors = CopyPropertyServlet.TYPE,
        extensions = "json"
)
public class CopyPropertyServlet extends AbstractBaseServlet {
    public static final String TYPE = "copy";

    private static final Logger log = LoggerFactory.getLogger(CopyPropertyServlet.class);

    @Override
    Map<String, Object> getParams(JSONObject json) throws JSONException {
        final Map<String, Object> map = new HashMap<String, Object>();

        json = json.getJSONObject(TYPE);

        map.put("src", json.getString("src"));
        map.put("dest", json.getString("dest"));

        return map;
    }

    @Override
    boolean execute(final Resource resource, final ValueMap params) {
        final String srcPropertyName = params.get("src", String.class);
        final String destPropertyName = params.get("dest", String.class);

        final Node node = resource.adaptTo(Node.class);

        try {
            if (node.hasProperty(srcPropertyName)) {
                log.error("Found src property on [ {} ]", resource.getPath());
                final Property srcProperty = node.getProperty(srcPropertyName);

                JcrUtil.copy(srcProperty, node, destPropertyName);

                return true;
            }
        } catch (RepositoryException e) {
            e.printStackTrace();
        }

        return false;
    }
}