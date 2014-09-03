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
import org.apache.commons.lang.StringUtils;
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

@SuppressWarnings("serial")
@SlingServlet(
        label = "ACS AEM Commons - Property Manager - Add Property Servlet",
        description = "...",
        methods = "POST",
        resourceTypes = "acs-commons/components/utilities/bulk-property-manager",
        selectors = FindAndReplaceServlet.TYPE,
        extensions = "json"
)
public class FindAndReplaceServlet extends AbstractBaseServlet {
    public static final String TYPE = "find-and-replace";
    public static final String SEARCH_STRING = "searchString";
    public static final String REPLACE_STRING = "replaceString";
    private static final Logger log = LoggerFactory.getLogger(FindAndReplaceServlet.class);

    @Override
    Map<String, Object> getParams(JSONObject json) throws JSONException {
        final Map<String, Object> map = new HashMap<String, Object>();

        json = json.getJSONObject(AddPropertyServlet.TYPE);

        map.put(SEARCH_STRING, json.getString("find"));
        map.put(REPLACE_STRING, json.getString("replace"));

        return map;
    }

    @Override
    Status execute(final Resource resource, final ValueMap params) {

        final String searchString = params.get(SEARCH_STRING, String.class);
        final String replaceString = params.get(REPLACE_STRING, String.class);

        boolean dirty = false;
        final ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);

        for (final Map.Entry<String, Object> entry : mvm.entrySet()) {

            if (entry.getValue() instanceof String) {

                /** String Value **/

                final String originalValue = (String) entry.getValue();
                final String newValue = originalValue.replaceAll(searchString, replaceString);

                if (!StringUtils.equals(originalValue, newValue)) {
                    if (canModifyProperties(resource)) {
                        mvm.put(entry.getKey(), newValue);
                        dirty = true;
                    } else {
                        return Status.ACCESS_ERROR;
                    }
                }

            } else if (entry.getValue() instanceof String[]) {

                /** String Array Value **/

                final String[] values = (String[]) entry.getValue();

                boolean dirtyArray = false;

                for (int i = 0; i < values.length; i++) {

                    final String originalValue = values[i];
                    final String newValue = originalValue.replaceAll(searchString, replaceString);

                    if (!StringUtils.equals(originalValue, newValue)) {
                        values[i] = newValue;
                        dirtyArray = true;
                    }
                }

                if (dirtyArray) {
                    // If any element in the Array is dirty (was replaced) then update the entire
                    // property and mark the overall process as being dirty
                    if (canModifyProperties(resource)) {
                        mvm.put(entry.getKey(), values);
                        dirty = true;
                    } else {
                        return Status.ACCESS_ERROR;
                    }
                }

            } // End property updating

        } // End for loop over each property on matching node

        if (dirty) {
            return Status.SUCCESS;
        } else {
            return Status.NOOP;
        }
    }
}
