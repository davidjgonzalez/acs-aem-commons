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

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

public class TraversalInclusionPolicy implements TreeTraverser.InclusionPolicy<Node> {
    private static final Logger log = LoggerFactory.getLogger(TraversalInclusionPolicy.class);

    public enum Operand {
        OR, AND
    }

    private final Operand operand;

    private final ResourceResolver resourceResolver;

    private final String nodeType;

    private final ValueMap properties;


    public TraversalInclusionPolicy(final ResourceResolver resourceResolver,
                                    final String nodeType,
                                    final Operand operand,
                                    final ValueMap properties) {
        this.operand = operand;
        this.resourceResolver = resourceResolver;
        this.nodeType = nodeType;
        this.properties = properties;
    }

    @Override
    public boolean include(final Node node) {
        try {
            final Resource resource = resourceResolver.getResource(node.getPath());

            if (StringUtils.isNotBlank(nodeType)
                    && !node.isNodeType(nodeType)) {
                return false;
            }

            final ValueMap resourceProperties = ResourceUtil.getValueMap(resource);

            if(this.properties.size() == 0) {
                // If no properties then don't check any and assume true
                return true;
            }

            for (final String name : this.properties.keySet()) {
                final String needle = this.properties.get(name, String.class);
                final String haystack = resourceProperties.get(name, String.class);

                final boolean match = this.isMatch(needle, haystack);

                if (match) {
                    if (Operand.OR.equals(this.operand)) {
                        // Return true on the first matching OR expression
                        return true;
                    }
                } else {
                    if (Operand.AND.equals(this.operand)) {
                        // Return false on the first non-matching AND expression
                        return false;
                    }
                }
            }

            if (Operand.OR.equals(this.operand)) {
                // Made it through all property checks without finding a match; so return false
                return false;
            } else {
                // Made it through all property checks without find a non-match; so return true
                return true;
            }

        } catch (RepositoryException e) {
            log.error("Could not include node");
            return false;
        }
    }

    protected final boolean isMatch(String needle, String haystack) {
        if (StringUtils.isBlank(needle) && StringUtils.isBlank(haystack)) {
            return true;
        } else if (StringUtils.isBlank(needle)) {
            return false;
        } else if (StringUtils.equals(needle, haystack)) {
            return true;
        } else if (haystack != null && haystack.matches(needle)) {
            return true;
        } else {
            return false;
        }
    }
}