/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2016 Adobe
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

package com.adobe.acs.commons.workflow.bulk.execution.model;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Model(adaptables = Resource.class)
public class PayloadGroup {
    private static final Logger log = LoggerFactory.getLogger(PayloadGroup.class);

    private Resource resource;

    @Inject
    @Optional
    private String next;

    public PayloadGroup(Resource resource) {
        this.resource = resource;
    }

    public String getPath() {
        return this.resource.getPath();
    }

    public PayloadGroup getNextPayloadGroup() {
        if (next == null) {
            return null;
        }

        Resource r = resource.getResourceResolver().getResource(next);

        if (r == null) {
            return null;
        }

        return r.adaptTo(PayloadGroup.class);
    }

    public Workspace getWorkspace() {
        return resource.getParent().adaptTo(Workspace.class);
    }

    public Payload getNextPayload() {
        for (Resource r : resource.getChildren()) {
            Payload payload = r.adaptTo(Payload.class);
            if (payload != null && !payload.isOnboarded()) {
                return payload;
            }
        }

        return null;
    }

    public List<Payload> getPayloads() {
        List<Payload> payloads = new ArrayList<Payload>();

        for (Resource r : resource.getChildren()) {
            Payload payload = r.adaptTo(Payload.class);
            if (payload != null) {
                payloads.add(payload);
            }
        }

        return payloads;
    }
}
