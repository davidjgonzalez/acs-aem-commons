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
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ModifiableValueMapDecorator;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AddPropertyServletTest {

    @Mock
    Resource resource;

    @Mock
    ResourceResolver resourceResolver;

    @Mock
    Session session;

    @Mock
    AccessControlManager accessControlManager;

    @Mock
    Privilege privilege;

    @Before
    public void setUp() throws Exception {
        when(resource.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
        when(session.getAccessControlManager()).thenReturn(accessControlManager);
        when(accessControlManager.privilegeFromName(Privilege.JCR_MODIFY_PROPERTIES)).thenReturn(privilege);
        when(accessControlManager.hasPrivileges(anyString(), any(Privilege[].class))).thenReturn(true);
    }

    @Test
    public void testExecute_newString() throws Exception {
        AddPropertyServlet servlet = new AddPropertyServlet();

        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());

        final String expected = "my value";

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("name", "myProp");
        params.put("value", servlet.getValueObject("String", "my value"));
        params.put("overwrite", false);

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        servlet.execute(resource, params);

        final String actual = mvm.get("myProp", String.class);

        assertEquals(expected, actual);
    }

    @Test
    public void testExecute_newLong() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

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

        assertTrue(actualObj instanceof Long);
        assertEquals(expected, actual);
    }

    @Test
    public void testExecute_NotOverwrite() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

        final String expected = "original value";

        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());
        mvm.put("myProp", expected);

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("name", "myProp");
        params.put("value", addPropertyServlet.getValueObject("String", "new value"));
        params.put("overwrite", false);

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        addPropertyServlet.execute(resource, params);

        final String actual = mvm.get("myProp", String.class);

        assertEquals(expected, actual);
    }

    @Test
    public void testExecute_Overwrite() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

        final String expected = "new value";

        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());
        mvm.put("myProp", "original value");

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("name", "myProp");
        params.put("value", addPropertyServlet.getValueObject("String", expected));
        params.put("overwrite", true);

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        addPropertyServlet.execute(resource, params);

        final String actual = mvm.get("myProp", String.class);

        assertEquals(expected, actual);
    }

    @Test
    public void testGetValueObject() throws Exception {
        AddPropertyServlet addPropertyServlet = new AddPropertyServlet();

        assertEquals("Hello World", addPropertyServlet.getValueObject("String", "Hello World"));
        assertEquals(9999, ((Long) addPropertyServlet.getValueObject("Long", "9999")).longValue());
        assertEquals(true, ((Boolean) addPropertyServlet.getValueObject("Boolean", "true")).booleanValue());
        assertEquals(false, ((Boolean) addPropertyServlet.getValueObject("Boolean", "false")).booleanValue());
    }
}