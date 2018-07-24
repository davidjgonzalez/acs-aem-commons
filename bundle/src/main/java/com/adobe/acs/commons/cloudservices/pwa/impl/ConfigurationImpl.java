package com.adobe.acs.commons.cloudservices.pwa.impl;

import com.day.cq.replication.ReplicationStatus;
import com.day.cq.wcm.api.Page;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import java.util.Calendar;

@Model(
        adaptables = {SlingHttpServletRequest.class}
)
public class ConfigurationImpl {

    @Self
    private SlingHttpServletRequest request;

    @Self
    private Resource resource;

    @ValueMapValue(name = "")
    private String foo;

    @ValueMapValue(name = "version")
    @Default(values = "1")
    private String version;

    @ValueMapValue(name = JcrConstants.JCR_LASTMODIFIED)
    Calendar lastModifiedDate;

    @ValueMapValue(name = "jcr:lastModifiedBy")
    String lastModifiedBy;

    @ValueMapValue(name = ReplicationStatus.NODE_PROPERTY_LAST_REPLICATED)
    Calendar lastPublishedDate;

    @ValueMapValue(name = ReplicationStatus.NODE_PROPERTY_LAST_REPLICATION_ACTION)
    String lastPublishedAction;


    public String getVersion() {
        return "v" + StringUtils.removeStartIgnoreCase("v", version);
    }

    public String getTitle() {
        final Page page = request.adaptTo(Page.class);

        if (page != null) {
            return page.getTitle();
        } else {
            return resource.getValueMap().get(JcrConstants.JCR_CONTENT + "/jcr:title", resource.getValueMap().get("jcr:title", resource.getName()));
        }
    }

    public Calendar getLastModifiedDate() {
       return lastModifiedDate;
    }

    public Calendar getLastPublishedDate() {
        if ("activate".equalsIgnoreCase(lastPublishedAction)) {
            return lastPublishedDate;
        } else {
            return null;
        }
    }

    public boolean hasChildren() {
        return false;
    }

}
