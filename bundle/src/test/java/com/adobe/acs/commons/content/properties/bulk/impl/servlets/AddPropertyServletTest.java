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

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ModifiableValueMapDecorator;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;

import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AddPropertyServletTest {

    @Test
    public void testExecute_newString() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

        Resource resource = mock(Resource.class);
        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());

        final String expected = "my value";

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("name", "myProp");
        params.put("value", addPropertyServlet.getValueObject("String", "my value"));
        params.put("overwrite", false);

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        addPropertyServlet.execute(resource, params);

        final String actual = mvm.get("myProp", String.class);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExecute_newLong() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

        Resource resource = mock(Resource.class);
        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());

        final Long expected = 9999L;

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("name", "myProp");
        params.put("value", addPropertyServlet.getValueObject("Long", "9999"));
        params.put("overwrite", false);

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        addPropertyServlet.execute(resource, params);

        final Object actualObj = mvm.get("myProp");
        final Long actual = mvm.get("myProp", Long.class);

        Assert.assertTrue(actualObj instanceof Long);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExecute_NotOverwrite() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

        final String expected = "original value";

        Resource resource = mock(Resource.class);
        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());
        mvm.put("myProp", expected);

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("name", "myProp");
        params.put("value", addPropertyServlet.getValueObject("String", "new value"));
        params.put("overwrite", false);

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        addPropertyServlet.execute(resource, params);

        final String actual = mvm.get("myProp", String.class);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExecute_Overwrite() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

        final String expected = "new value";

        Resource resource = mock(Resource.class);
        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());
        mvm.put("myProp", "original value");

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("name", "myProp");
        params.put("value", addPropertyServlet.getValueObject("String", expected));
        params.put("overwrite", true);

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        addPropertyServlet.execute(resource, params);

        final String actual = mvm.get("myProp", String.class);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testGetValueObject() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

        Assert.assertEquals("Hello World", addPropertyServlet.getValueObject("String", "Hello World"));
        Assert.assertEquals(9999, ((Long) addPropertyServlet.getValueObject("Long", "9999")).longValue());
        Assert.assertEquals(true, ((Boolean) addPropertyServlet.getValueObject("Boolean", "true")).booleanValue());
        Assert.assertEquals(false, ((Boolean) addPropertyServlet.getValueObject("Boolean", "false")).booleanValue());
    }
}