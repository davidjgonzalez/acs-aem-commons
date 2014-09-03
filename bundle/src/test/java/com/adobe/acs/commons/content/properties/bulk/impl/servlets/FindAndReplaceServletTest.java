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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FindAndReplaceServletTest {
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
         public void testExecute_fullStringReplace() throws Exception {
        FindAndReplaceServlet servlet = new FindAndReplaceServlet();

        final String expected = "new value";

        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());
        mvm.put("myProp", "original value");

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("searchString", "original value");
        params.put("replaceString", expected);

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        servlet.execute(resource, params);

        final String actual = mvm.get("myProp", String.class);
        assertEquals(expected, actual);
    }

    @Test
    public void testExecute_partialStringReplace() throws Exception {
        FindAndReplaceServlet servlet = new FindAndReplaceServlet();

        final String expected = "new value";

        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());
        mvm.put("myProp", "original value");

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("searchString", "original");
        params.put("replaceString", "new");

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        servlet.execute(resource, params);

        final String actual = mvm.get("myProp", String.class);
        assertEquals(expected, actual);
    }


    @Test
    public void testExecute_fullStringArrayReplace() throws Exception {
        FindAndReplaceServlet servlet = new FindAndReplaceServlet();

        final String[] expected = {"hello", "new value", "world"};

        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());
        mvm.put("myProp", new String[] {"hello", "original value", "world"});

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("searchString", "original value");
        params.put("replaceString", "new value");

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        servlet.execute(resource, params);

        final String[] actual = mvm.get("myProp", String[].class);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testExecute_partialStringArrayReplace() throws Exception {
        FindAndReplaceServlet servlet = new FindAndReplaceServlet();

        final String[] expected = {"hello", "my new value", "world"};

        ModifiableValueMap mvm = new ModifiableValueMapDecorator(new HashMap<String, Object>());
        mvm.put("myProp", new String[] {"hello", "my original value", "world"});

        final ValueMap params = new ValueMapDecorator(new HashMap<String, Object>());
        params.put("searchString", "original");
        params.put("replaceString", "new");

        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(mvm);

        servlet.execute(resource, params);

        final String[] actual = mvm.get("myProp", String[].class);
        assertArrayEquals(expected, actual);
    }
}