package com.adobe.acs.commons.cloudservices.pwa;

import com.day.cq.wcm.api.Page;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.annotation.versioning.ProviderType;

import java.util.Calendar;

/**
 * This calls represents the PWA /conf configuration page at /conf/<tenant>/cloudconfigs/pwa.
 */
@ProviderType
public interface Configuration {

    /**
     * Note this will return FALSE On AEM Author, as caching of AEM Author requests has a high probability of breaking the AEM authoring experience.
     * @return true is the PWA Configuration should be rendered on the included AEM page.
     */
    boolean isReady();

    /**
     * @return the /conf Page that defines the PWA's configuration.
     */
    Page getConfPage();

    /**
     * @return the Properties of the PWA Conf page.
     */
    ValueMap getProperties();

    /**
     * @return the Resource Resolver mapped path that defines the PWA's scope.
     */
    String getScopePath();

    /**
     ** @return A list of all Client Library categories whose JavaScript should be included to expose the Service Worker JavaScript.
     */
    String[] getServiceWorkerJsCategories();

    /**
     * @return A list of all Client Library categories whose JavaScript should be included to expose the page-included PWA JavaScript.
     */
    String[] getPwaJsCategories();

    /**
     * @return the last modified time of the resolved PWA configuration page under /conf.
     */
    Calendar getLastModified();
}