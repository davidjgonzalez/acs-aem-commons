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
package com.adobe.acs.commons.errors.errorhandler.impl;

import com.adobe.acs.commons.errorpagehandler.ErrorPageHandlerService;
import com.adobe.acs.commons.errorpagehandler.cache.impl.ErrorPageCache;
import com.adobe.acs.commons.errorpagehandler.cache.impl.ErrorPageCacheImpl;
import com.adobe.acs.commons.errors.errorhandler.ErrorHandler;
import com.adobe.acs.commons.errors.errorhandler.ErrorResponse;
import com.adobe.acs.commons.util.InfoWriter;
import com.adobe.acs.commons.wcm.ComponentHelper;
import com.day.cq.commons.PathInfo;
import com.day.cq.commons.inherit.HierarchyNodeInheritanceValueMap;
import com.day.cq.commons.inherit.InheritanceValueMap;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.search.QueryBuilder;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
import org.apache.sling.auth.core.AuthUtil;
import org.apache.sling.commons.auth.Authenticator;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.DynamicMBean;
import javax.management.NotCompliantMBeanException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component(
        label = "ACS AEM Commons - Error Page Handler",
        description = "Error Page Handling module which facilitates the resolution of errors "
                + "against author-able pages for discrete content trees.",
        immediate = false, metatype = true)
@Service
public final class ErrorHandlerEngineImpl implements ErrorPageHandlerService {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlerEngineImpl.class);

    public static final String DEFAULT_ERROR_PAGE_NAME = "errors";

    public static final String ERROR_PAGE_PROPERTY = "errorPages";

    private static final String REDIRECT_TO_LOGIN = "redirect-to-login";
    private static final String RESPOND_WITH_404 = "respond-with-404";

    /* Enable/Disable */
    private static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    @Property(label = "Enable", description = "Enables/Disables the error handler. [Required]",
            boolValue = DEFAULT_ENABLED)
    private static final String PROP_ENABLED = "enabled";

    /* Error Page Extension */
    private static final String DEFAULT_ERROR_PAGE_EXTENSION = "html";

    private String errorPageExtension = DEFAULT_ERROR_PAGE_EXTENSION;

    @Property(label = "Error page extension",
            description = "Examples: html, htm, xml, json. [Optional] [Default: html]",
            value = DEFAULT_ERROR_PAGE_EXTENSION)
    private static final String PROP_ERROR_PAGE_EXTENSION = "error-page.extension";

    /* Fallback Error Code Extension */
    private static final String DEFAULT_FALLBACK_ERROR_NAME = "500";

    private String fallbackErrorName = DEFAULT_FALLBACK_ERROR_NAME;

    @Property(
            label = "Fallback error page name",
            description = "Error page name (not path) to use if a valid Error Code/Error Servlet Name cannot be "
                    + "retrieved from the Request. [Required] [Default: 500]",
            value = DEFAULT_FALLBACK_ERROR_NAME)
    private static final String PROP_FALLBACK_ERROR_NAME = "error-page.fallback-name";

    /* System Error Page Path */
    private static final String DEFAULT_SYSTEM_ERROR_PAGE_PATH_DEFAULT = "";

    private String systemErrorPagePath = DEFAULT_SYSTEM_ERROR_PAGE_PATH_DEFAULT;

    @Property(
            label = "System error page",
            description = "Absolute path to system Error page resource to serve if no other more appropriate "
                    + "error pages can be found. Does not include extension. [Optional... but highly recommended]",
            value = DEFAULT_SYSTEM_ERROR_PAGE_PATH_DEFAULT)
    private static final String PROP_ERROR_PAGE_PATH = "error-page.system-path";

    /* Search Paths */
    private static final String[] DEFAULT_SEARCH_PATHS = {};

    @Property(
            label = "Error page paths",
            description = "List of inclusive content trees under which error pages may reside, "
                    + "along with the name of the the default error page for the content tree. This is a "
                    + "fallback/less powerful option to adding the ./errorPages property to CQ Page property dialogs."
                    + " Example: /content/geometrixx/en:errors [Optional]",
            cardinality = Integer.MAX_VALUE)
    private static final String PROP_SEARCH_PATHS = "paths";

    /* Not Found Default Behavior */
    private static final String DEFAULT_NOT_FOUND_DEFAULT_BEHAVIOR = RESPOND_WITH_404;
    private String notFoundBehavior = DEFAULT_NOT_FOUND_DEFAULT_BEHAVIOR;

    @Property(
            label = "Not Found Behavior",
            description = "Default resource not found behavior. [Default: Respond with 404]",
            options = {
                    @PropertyOption(value = "Redirect to Login", name = REDIRECT_TO_LOGIN),
                    @PropertyOption(value = "Respond with 404", name = RESPOND_WITH_404)
            })
    private static final String PROP_NOT_FOUND_DEFAULT_BEHAVIOR = "not-found.behavior";


    /* Not Found Path Patterns */
    private static final String[] DEFAULT_NOT_FOUND_EXCLUSION_PATH_PATTERNS = {};
    private ArrayList<Pattern> notFoundExclusionPatterns = new ArrayList<Pattern>();

    @Property(
            label = "Not Found Exclusions",
            description = "Regex path patterns that will act in the \"other\" (redirect-to-login vs. "
                    + " respond-with-404) way to the \"Not Found Behavior\". This allows the usual Not Found behavior"
                    + " to be defined via \"not-found.behavior\" with specific exclusions defined here. [Optional]",
            cardinality = Integer.MAX_VALUE)
    private static final String PROP_NOT_FOUND_EXCLUSION_PATH_PATTERNS = "not-found.exclusion-path-patterns";


    private static final int DEFAULT_TTL = 60 * 5; // 5 minutes

    private static final boolean DEFAULT_SERVE_AUTHENTICATED_FROM_CACHE = false;

    @Property(label = "Serve authenticated from cache",
            description = "Serve authenticated requests from the error page cache. [ Default: false ]",
            boolValue = DEFAULT_SERVE_AUTHENTICATED_FROM_CACHE)
    private static final String PROP_SERVE_AUTHENTICATED_FROM_CACHE = "cache.serve-authenticated";
    private static final String LEGACY_PROP_SERVE_AUTHENTICATED_FROM_CACHE = "serve-authenticated-from-cache";

    @Property(label = "TTL (in seconds)",
            description = "TTL for each cache entry in seconds. [ Default: 300 ]",
            intValue = DEFAULT_TTL)
    private static final String PROP_TTL = "cache.ttl";
    private static final String LEGACY_PROP_TTL = "ttl";

    /* Enable/Disables error images */
    private static final boolean DEFAULT_ERROR_IMAGES_ENABLED = false;

    private boolean errorImagesEnabled = DEFAULT_ERROR_IMAGES_ENABLED;

    @Property(label = "Enable placeholder images", description = "Enable image error handling  [ Default: false ]",
            boolValue = DEFAULT_ERROR_IMAGES_ENABLED)
    private static final String PROP_ERROR_IMAGES_ENABLED = "error-images.enabled";

    /* Relative placeholder image path */
    private static final String DEFAULT_ERROR_IMAGE_PATH = ".img.png";

    private String errorImagePath = DEFAULT_ERROR_IMAGE_PATH;

    @Property(label = "Error image path/selector",
            description = "Accepts a selectors.extension (ex. `.img.png`) absolute, or relative path. "
                    + "If an extension or relative path, this value is applied to the resolved error page."
                    + " Note: This concatenated path must resolve to a nt:file else a 200 response will be sent."
                    + " [ Optional ] [ Default: .img.png ]",
            value = DEFAULT_ERROR_IMAGE_PATH)
    private static final String PROP_ERROR_IMAGE_PATH = "error-images.path";

    /* Error image extensions to handle */
    private static final String[] DEFAULT_ERROR_IMAGE_EXTENSIONS = {"jpg", "jpeg", "png", "gif"};

    private String[] errorImageExtensions = DEFAULT_ERROR_IMAGE_EXTENSIONS;

    @Property(
            label = "Error image extensions",
            description = "List of valid image extensions (no proceeding .) to handle. "
                    + "Example: 'png' "
                    + "[ Optional ] [ Default: png, jpeg, jpeg, gif ]",
            cardinality = Integer.MAX_VALUE,
            value = { "png", "jpeg", "jpg", "gif" })
    private static final String PROP_ERROR_IMAGE_EXTENSIONS = "error-images.extensions";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private Authenticator authenticator;

    @Reference
    private ComponentHelper componentHelper;

    private ErrorPageCache cache;

    private SortedMap<String, String> pathMap = new TreeMap<String, String>();

    private ServiceRegistration cacheRegistration;


    private Map<String, ErrorHandler> errorHandlers = new TreeMap<String, ErrorHandler>();

    /**
     * Find the JCR full path to the most appropriate Error Page.
     *
     * @param request
     * @param errorResource
     * @return
     */
    public String resolveErrorPath(SlingHttpServletRequest request, Resource errorResource) {
        if (!this.enabled) {
            return null;
        }

        for (final ErrorHandler errorHandler : this.errorHandlers.values()) {
            log.debug("Processing error handler [ {} ]", errorHandler.getClass().getName());

            if (errorHandler.accepts(request, errorResource)) {
                String path = errorHandler.getPath(request, errorResource);

                if (StringUtils.isNotBlank(path)) {
                    // Found a non-blank path; return it for use
                    return path;
                }
            }
        }

        return null;
    }



    /** HTTP Request Data Retrieval Methods **/

    /**
     * Get Error Status Code from Request or Default (500) if no status code can be found.
     *
     * @param request
     * @return
     */
    public int getStatusCode1(SlingHttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute(SlingConstants.ERROR_STATUS);

        if (statusCode != null) {
            return statusCode;
        } else {
            return ErrorPageHandlerService.DEFAULT_STATUS_CODE;
        }
    }










    /** Script Support Methods **/

    /**
     * Determines if the request has been authenticated or is Anonymous.
     *
     * @param request
     * @return
     */
    protected boolean isAnonymousRequest(SlingHttpServletRequest request) {
        return (request.getAuthType() == null || request.getRemoteUser() == null);
    }

    /**
     * Attempts to invoke a valid Sling Authentication Handler for the request.
     *
     * @param request
     * @param response
     *
     * @return true if the request will be authenticated, false is the request could not trigger authentication
     */
    protected boolean authenticateRequest(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        if (authenticator == null) {
            log.warn("Cannot login: Missing Authenticator service");
            return false;
        }

        authenticator.login(request, response);
        return true;
    }

    /**
     * Determine is the request is a 404 and if so handles the request appropriately base on some CQ idiosyncrasies.
     * <p>
     * Mainly forces an authentication request in Authoring modes (!WCMMode.DISABLED)
     * @param request
     * @param response
     */
    @Override
    public boolean doHandle404(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        String path = request.getResource().getPath();

        if (StringUtils.isBlank(path)) {
            path = request.getPathInfo();
        }


        if (log.isDebugEnabled()) {

            InfoWriter iw = new InfoWriter();

            iw.title("ACS AEM Commons - Error Page Handler 404 Handling");

            iw.message("Status code: {}", this.getStatusCode(request));
            iw.message("Is anonymous: {}", isAnonymousRequest(request));
            iw.message("Is browser request: {}", AuthUtil.isBrowserRequest(request));
            iw.message("Is redirect to login page: {}", this.isRedirectToLogin(path));
            iw.message("Default 404 Behavior: {}", this.notFoundBehavior);

            iw.line();

            log.debug(iw.toString());
        }

        if (this.getStatusCode(request) == SlingHttpServletResponse.SC_NOT_FOUND
                && this.isAnonymousRequest(request)
                && AuthUtil.isBrowserRequest(request)
                && this.isRedirectToLogin(path)) {

            // Authenticate Request
            // If an authenticator cannot be found, then process as a normal 404
            return !authenticateRequest(request, response);

        } else {
            log.debug("Allow error page handler to handle request");

            return true;
        }
    }

    /**
     * Determines if the request should redirect to login or respond with 404 based on the Error Page Handler's config.
     *
     * @param path the request path
     * @return true to indicate a redirect to login, false to indicate a respond w 404
     */
    private boolean isRedirectToLogin(final String path) {
        log.debug("Not Found Behavior: {}", this.notFoundBehavior);

        if (StringUtils.equals(REDIRECT_TO_LOGIN, this.notFoundBehavior)) {
            // Default behavior redirect to login
            for (final Pattern p : this.notFoundExclusionPatterns) {
                final Matcher m = p.matcher(path);
                if (m.matches()) {
                    // Path is an exclusion to "redirect to login" ~> "respond w/ 404"
                    log.debug("Path is an exclusion to \"redirect to login\" ~> \"respond w/ 404\"");
                    return false;
                }
            }
            // Path did NOT match exclusions for "redirect to login" ~> "redirect to login"
            log.debug("Path did NOT match exclusions for \"redirect to login\" ~> \"redirect to login\"");
            return true;
        } else {
            // Default behavior is to respond w/ 404
            for (final Pattern p : this.notFoundExclusionPatterns) {
                final Matcher m = p.matcher(path);
                if (m.matches()) {
                    // Path is an exclusion to "respond w/ 404" ~> "redirect to login"
                    log.debug("Path is an exclusion to \"respond w/ 404\" ~> \"redirect to login\"");
                    return true;
                }
            }

            // Path did NOT match exclusions for "respond w/ 404" ~> "respond w/ 404"
            log.debug("Path did NOT match exclusions for \"respond w/ 404\" ~> \"respond w/ 404\"");
            return false;
        }
    }

    /**
     * Returns the Exception Message (Stacktrace) from the Request.
     *
     * @param request
     * @return
     */
    @Override
    public String getException(SlingHttpServletRequest request) {
        StringWriter stringWriter = new StringWriter();
        if (request.getAttribute(SlingConstants.ERROR_EXCEPTION) instanceof Throwable) {
            Throwable throwable = (Throwable) request.getAttribute(SlingConstants.ERROR_EXCEPTION);

            if (throwable == null) {
                return "";
            }

            if (throwable instanceof ServletException) {
                ServletException se = (ServletException) throwable;
                while (se.getRootCause() != null) {
                    throwable = se.getRootCause();
                    if (throwable instanceof ServletException) {
                        se = (ServletException) throwable;
                    } else {
                        break;
                    }
                }
            }

            throwable.printStackTrace(new PrintWriter(stringWriter, true));
        }

        return stringWriter.toString();
    }

    /**
     * Returns a String representation of the RequestProgress trace.
     *
     * @param request
     * @return
     */
    public String getRequestProgress(SlingHttpServletRequest request) {
        StringWriter stringWriter = new StringWriter();
        if (request != null) {
            RequestProgressTracker tracker = request.getRequestProgressTracker();
            tracker.dump(new PrintWriter(stringWriter, true));
        }
        return stringWriter.toString();
    }
    .

    @Activate
    protected void activate(ComponentContext componentContext) {
        configure(componentContext);
    }

    @Deactivate
    protected void deactivate(ComponentContext componentContext) {
        enabled = false;
        if (cacheRegistration != null) {
            cacheRegistration.unregister();
            cacheRegistration = null;
        }
    }

    private void configure(ComponentContext componentContext) {
        Dictionary<?, ?> config = componentContext.getProperties();
        final String legacyPrefix = "prop.";

        this.enabled = PropertiesUtil.toBoolean(config.get(PROP_ENABLED),
                PropertiesUtil.toBoolean(config.get(legacyPrefix + PROP_ENABLED),
                        DEFAULT_ENABLED));

        /** Error Pages **/

        this.systemErrorPagePath = PropertiesUtil.toString(config.get(PROP_ERROR_PAGE_PATH),
                PropertiesUtil.toString(config.get(legacyPrefix + PROP_ERROR_PAGE_PATH),
                        DEFAULT_SYSTEM_ERROR_PAGE_PATH_DEFAULT));

        this.errorPageExtension = PropertiesUtil.toString(config.get(PROP_ERROR_PAGE_EXTENSION),
                PropertiesUtil.toString(config.get(legacyPrefix + PROP_ERROR_PAGE_EXTENSION),
                        DEFAULT_ERROR_PAGE_EXTENSION));

        this.fallbackErrorName = PropertiesUtil.toString(config.get(PROP_FALLBACK_ERROR_NAME),
                PropertiesUtil.toString(config.get(legacyPrefix + PROP_FALLBACK_ERROR_NAME),
                        DEFAULT_FALLBACK_ERROR_NAME));

        this.pathMap = configurePathMap(PropertiesUtil.toStringArray(config.get(PROP_SEARCH_PATHS),
                PropertiesUtil.toStringArray(config.get(legacyPrefix + PROP_SEARCH_PATHS),
                        DEFAULT_SEARCH_PATHS)));

        /** Not Found Handling **/
        this.notFoundBehavior = PropertiesUtil.toString(config.get(PROP_NOT_FOUND_DEFAULT_BEHAVIOR),
                DEFAULT_NOT_FOUND_DEFAULT_BEHAVIOR);

        String[] tmpNotFoundExclusionPatterns = PropertiesUtil.toStringArray(
                config.get(PROP_NOT_FOUND_EXCLUSION_PATH_PATTERNS), DEFAULT_NOT_FOUND_EXCLUSION_PATH_PATTERNS);

        this.notFoundExclusionPatterns = new ArrayList<Pattern>();
        for (final String tmpPattern : tmpNotFoundExclusionPatterns) {
            this.notFoundExclusionPatterns.add(Pattern.compile(tmpPattern));
        }


        /** Error Page Cache **/

        int ttl = PropertiesUtil.toInteger(config.get(PROP_TTL),
                PropertiesUtil.toInteger(LEGACY_PROP_TTL, DEFAULT_TTL));

        boolean serveAuthenticatedFromCache = PropertiesUtil.toBoolean(config.get(PROP_SERVE_AUTHENTICATED_FROM_CACHE),
                PropertiesUtil.toBoolean(LEGACY_PROP_SERVE_AUTHENTICATED_FROM_CACHE,
                        DEFAULT_SERVE_AUTHENTICATED_FROM_CACHE));
        try {
            cache = new ErrorPageCacheImpl(ttl, serveAuthenticatedFromCache);

            Dictionary<String, Object> serviceProps = new Hashtable<String, Object>();
            serviceProps.put("jmx.objectname", "com.adobe.acs.commons:type=ErrorPageHandlerCache");

            cacheRegistration = componentContext.getBundleContext().registerService(DynamicMBean.class.getName(),
                    cache, serviceProps);
        } catch (NotCompliantMBeanException e) {
            log.error("Unable to create cache", e);
        }

        /** Error Images **/

        this.errorImagesEnabled = PropertiesUtil.toBoolean(config.get(PROP_ERROR_IMAGES_ENABLED),
                DEFAULT_ERROR_IMAGES_ENABLED);

        this.errorImagePath = PropertiesUtil.toString(config.get(PROP_ERROR_IMAGE_PATH),
                DEFAULT_ERROR_IMAGE_PATH);

        // Absolute path
        if (StringUtils.startsWith(this.errorImagePath, "/")) {
            ResourceResolver adminResourceResolver = null;
            try {
                adminResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
                final Resource resource = adminResourceResolver.resolve(this.errorImagePath);

                if (resource != null && resource.isResourceType(JcrConstants.NT_FILE)) {
                    final PathInfo pathInfo = new PathInfo(this.errorImagePath);

                    if (!StringUtils.equals("img", pathInfo.getSelectorString())
                            || StringUtils.isBlank(pathInfo.getExtension())) {

                        log.warn("Absolute Error Image Path paths to nt:files should have '.img.XXX' "
                                + "selector.extension");
                    }
                }
            } catch (LoginException e) {
                log.error("Could not get admin resource resolver to inspect validity of absolute errorImagePath");
            } finally {
                if (adminResourceResolver != null) {
                    adminResourceResolver.close();
                }
            }
        }

        this.errorImageExtensions = PropertiesUtil.toStringArray(config.get(PROP_ERROR_IMAGE_EXTENSIONS),
                DEFAULT_ERROR_IMAGE_EXTENSIONS);

        for (int i = 0; i < errorImageExtensions.length; i++) {
            this.errorImageExtensions[i] = StringUtils.lowerCase(errorImageExtensions[i], Locale.ENGLISH);
        }

        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);

        pw.println();
        pw.printf("Enabled: %s", this.enabled).println();
        pw.printf("System Error Page Path: %s", this.systemErrorPagePath).println();
        pw.printf("Error Page Extension: %s", this.errorPageExtension).println();
        pw.printf("Fallback Error Page Name: %s", this.fallbackErrorName).println();

        pw.printf("Resource Not Found - Behavior: %s", this.notFoundBehavior).println();
        pw.printf("Resource Not Found - Exclusion Path Patterns %s", Arrays.toString(tmpNotFoundExclusionPatterns)).println();

        pw.printf("Cache - TTL: %s", ttl).println();
        pw.printf("Cache - Serve Authenticated: %s", serveAuthenticatedFromCache).println();

        pw.printf("Error Images - Enabled: %s", this.errorImagesEnabled).println();
        pw.printf("Error Images - Path: %s", this.errorImagePath).println();
        pw.printf("Error Images - Extensions: %s", Arrays.toString(this.errorImageExtensions)).println();

        log.debug(sw.toString());
    }

    /**
     * Convert OSGi Property storing Root content paths:Error page paths into a SortMap.
     *
     * @param paths
     * @return
     */
    private SortedMap<String, String> configurePathMap(String[] paths) {
        SortedMap<String, String> sortedMap = new TreeMap<String, String>(new StringLengthComparator());

        for (String path : paths) {
            if (StringUtils.isBlank(path)) {
                continue;
            }

            final SimpleEntry<String, String> tmp = toSimpleEntry(path, ":");

            if (tmp == null) {
                continue;
            }

            String key = StringUtils.strip(tmp.getKey());
            String val = StringUtils.strip(tmp.getValue());

            // Only accept absolute paths
            if (StringUtils.isBlank(key) || !StringUtils.startsWith(key, "/")) {
                continue;
            }

            // Validate page name value
            if (StringUtils.isBlank(val)) {
                val = key + "/" + DEFAULT_ERROR_PAGE_NAME;
            } else if (StringUtils.equals(val, ".")) {
                val = key;
            } else if (!StringUtils.startsWith(val, "/")) {
                val = key + "/" + val;
            }

            sortedMap.put(key, val);
        }

        return sortedMap;
    }

    public void includeUsingGET(final SlingHttpServletRequest request, final SlingHttpServletResponse response,
                                final ErrorResponse errorResponse) {
        if (cache == null || !errorResponse.isCacheable()) {
            final RequestDispatcher dispatcher = request.getRequestDispatcher(errorResponse.getPath());

            try {
                dispatcher.include(new GetRequest(request), response);
            } catch (Exception e) {
                log.debug("Exception swallowed while including error page", e);
            }

        } else {
            final String responseData = cache.get(errorResponse.getPath(), new GetRequest(request), response);
            try {
                response.getWriter().write(responseData);
            } catch (Exception e) {
                log.info("Exception swallowed while including error page", e);
            }
        }
    }

    /**
     * Forces request to behave as a GET Request.
     */
    private static class GetRequest extends SlingHttpServletRequestWrapper {

        public GetRequest(SlingHttpServletRequest wrappedRequest) {
            super(wrappedRequest);
        }

        @Override
        public String getMethod() {
            return "GET";
        }
    }

}
