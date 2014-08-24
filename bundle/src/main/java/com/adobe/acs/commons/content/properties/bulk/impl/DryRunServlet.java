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


import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.Map;

@SlingServlet(
        label = "ACS AEM Commons - Bulk Property Manager - DryRun Servlet",
        description = "...",
        methods = "POST",
        resourceTypes = "acs-commons/components/utilities/bulk-property-manager",
        selectors = DryRunServlet.TYPE,
        extensions = "csv"
)
public class DryRunServlet extends AbstractBaseServlet {
    private static final Logger log = LoggerFactory.getLogger(DryRunServlet.class);

    public static final String TYPE = "dry-run";

    @Override
    Map<String, Object> getParams(JSONObject json) throws JSONException {
        final Map<String, Object> map = new HashMap<String, Object>();

        map.put(KEY_DRY_RUN, true);

        return map;
    }

    @Override
    boolean execute(final Resource resource, final ValueMap params) {
        try {
            return canModifyProperties(resource);
        } catch (RepositoryException e) {
            return false;
        }
    }
}