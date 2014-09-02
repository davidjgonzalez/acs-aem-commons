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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.testing.sling.MockResource;
import org.apache.sling.commons.testing.sling.MockResourceResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class TraversalInclusionPolicyTest {

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testInclude() throws Exception {
        ResourceResolver resourceResolver = new MockResourceResolver();
        Resource resource1 = new MockResource(resourceResolver, "/content/resource1", "test/resourceType");
        Resource resource2 = new MockResource(resourceResolver, "/content/resource2", "test/resourceType");

        ValueMap properties = new ValueMapDecorator(new HashMap<String, Object>());
        properties.put("foo", "bar");

        TraversalInclusionPolicy tip = new TraversalInclusionPolicy(resourceResolver,
                "nt:unstructed",
                TraversalInclusionPolicy.Operand.OR,
                properties);

    }

    @Test
    public void testIsMatch() throws Exception {
        TraversalInclusionPolicy tip = new TraversalInclusionPolicy(null, null, null, null);

        // Blanks and Nulls
        assertTrue(tip.isMatch(null, null));
        assertTrue(tip.isMatch(null, ""));
        assertTrue(tip.isMatch("", null));

        assertFalse(tip.isMatch("foo", null));
        assertFalse(tip.isMatch("foo", ""));
        assertFalse(tip.isMatch(null, "bar"));
        assertFalse(tip.isMatch("", "bar"));

        // Straight property matches
        assertTrue(tip.isMatch("foo", "foo"));
        assertFalse(tip.isMatch("foo", "bar"));

        // Regexs
        assertTrue(tip.isMatch("foo.*", "foobar"));
        assertTrue(tip.isMatch("[f]o+", "foo"));
        assertFalse(tip.isMatch("foo.*", "bar"));
    }
}