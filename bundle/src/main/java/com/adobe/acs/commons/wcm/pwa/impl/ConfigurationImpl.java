package com.adobe.acs.commons.wcm.pwa.impl;

import com.adobe.acs.commons.wcm.pwa.Configuration;
import com.adobe.granite.confmgr.Conf;
import com.adobe.granite.confmgr.ConfMgr;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.wcm.api.Page;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.*;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

@Model(
        adaptables = { SlingHttpServletRequest.class },
        adapters = { Configuration.class }
)
public class ConfigurationImpl implements Configuration {
    @Self
    private SlingHttpServletRequest request;

    @SlingObject
    private ResourceResolver resourceResolver;

    @SlingObject
    private Resource resource;

    @ScriptVariable
    @Optional
    private Page currentPage;

    @RequestAttribute
    private Resource useResource;

    @OSGiService
    private ConfMgr confMgr;

    public String getTitle() {
        return getResource().getValueMap().get("jcr:content/jcr:title", getResource().getValueMap().get("jcr:title", getResource().getName()));
    }

    public boolean hasChildren() {
        if (getResource().hasChildren()) {
            for (Resource child : getResource().getChildren()) {
                boolean isContainer = ResourceHelper.isConfigurationContainer(child);
                boolean hasSetting = ResourceHelper.hasSetting(child,"settings");
                if (isContainer || hasSetting) {
                    return true;
                }
            }
        }
        return false;
    }

    public Calendar getLastModifiedDate() {
        Page page = getResource().adaptTo(Page.class);
        if (page != null) {
            return page.getLastModified();
        }
        ValueMap props = getResource().adaptTo(ValueMap.class);
        if (props != null) {
            return props.get(JcrConstants.JCR_LASTMODIFIED, Calendar.class);
        }
        return null;
    }

    public String getLastModifiedBy() {
        Page page = getResource().adaptTo(Page.class);
        if (page != null) {
            return page.getLastModifiedBy();
        }
        ValueMap props = getResource().adaptTo(ValueMap.class);
        if (props != null) {
            return props.get(JcrConstants.JCR_LAST_MODIFIED_BY, String.class);
        }
        return null;
    }

    public Set<String> getQuickactionsRels() {
        Set<String> quickactions = new LinkedHashSet<String>();

        if (ResourceHelper.isCloudConfiguration(getResource())) {
            quickactions.add("cq-confadmin-actions-properties-activator");
        }

        return quickactions;
    }

    @Override
    public String getPwaJavaScriptPath() {
        return null;
    }

    @Override
    public String getPwaRootPagePath() {
        Page pageWithConf = currentPage;

        while(pageWithConf != null) {
            if (pageWithConf.getProperties().get("cq:conf", String.class) != null) {
                // cq:conf is set
                Conf conf = confMgr.getConf(pageWithConf.getContentResource());
                if (conf != null) {
                    if (conf.getItemResource("pwa/pwa") != null) {
                        return pageWithConf.getPath();
                    }
                }
            }

            pageWithConf = pageWithConf.getParent();
        }

        return null;
    }


    @Override
    public String getServiceWorkerPath() {
        return getPwaRootPagePath() + ".pwa.load/service-worker.js";
    }

    private Resource getResource() {
        if (useResource != null) {
            return useResource;
        }
        return resource;
    }

}