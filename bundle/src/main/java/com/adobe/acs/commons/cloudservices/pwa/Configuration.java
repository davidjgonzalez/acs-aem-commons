package com.adobe.acs.commons.cloudservices.pwa;

import java.util.Calendar;

public interface Configuration {

    String  getTitle();

    Calendar getLastModifiedDate();

    Calendar getLastPublishedDate();

    boolean hasChildren();

    /**  **/

    String getVersion();

}
