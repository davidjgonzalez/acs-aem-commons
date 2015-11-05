package com.adobe.acs.commons.images.screenshots.impl;


import com.adobe.acs.commons.images.screenshots.Screenshot;
import com.adobe.acs.commons.util.ResourceDataUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * http://phantomjs.org/screen-capture.html
 * <p/>
 * var page = require('webpage').create(),
 * system = require('system'),
 * userName,
 * password,
 * uri,
 * imagePath;
 * <p/>
 * if (system.args.length < 3) {
 * phantom.exit(1);
 * } else {
 * imagePath = system.args[1];
 * uri = system.args[2];
 * <p/>
 * if (system.args.length === 5) {
 * userName = system.args[3];
 * password = system.args[4];
 * page.customHeaders={'Authorization': 'Basic '+btoa(userName + ':' + password)};
 * }
 * <p/>
 * page.settings.resourceTimeout = 10000; // 10 seconds
 * page.zoomFactor = 1;
 * page.viewportSize = {
 * width: 1024,
 * height: 786
 * };
 * <p/>
 * page.open(uri, function() {
 * page.render(imagePath);
 * phantom.exit();
 * });
 * }
 */

@Component
@Service
public class PhantomJSScreenshotImpl implements Screenshot {
    private static final Logger log = LoggerFactory.getLogger(PhantomJSScreenshotImpl.class);

    private static final String TMP_FILE_PREFIX = "acs-commons_screenshot-";


    private static final String DEFAULT_EXECUTABLE = "/usr/local/bin/node_modules/phantomjs/bin/phantomjs";

    private String executable = DEFAULT_EXECUTABLE;

    @Property(label = "PhantomJS Executable Location",
            description = "",
            value = DEFAULT_EXECUTABLE)
    public static final String PROP_EXECUTABLE = "phantomjs.executable";


    private static final String DEFAULT_WORKING_FOLDER =
            StringUtils.defaultIfEmpty(System.getProperty("java.io.tmpdir"), "/tmp");

    private String workingFolder = DEFAULT_WORKING_FOLDER;

    @Property(label = "Working Folder",
            description = "",
            value = "/tmp")
    public static final String PROP_WORKING_FOLDER = "working-folder";


    /**
     * @param resourceResolver
     * @param uri
     * @return
     * @throws IOException
     */
    public InputStream takeScreenshot(ResourceResolver resourceResolver, String scriptResourcePath, Type
            screenshotType,
                                      String...
                                              params) throws
            IOException, RepositoryException, InterruptedException {

        final String scriptContents = ResourceDataUtil.getNTFileAsString(
                resourceResolver.getResource(scriptResourcePath));

        // Create the script file
        final Path scriptPath = this.createScript(workingFolder, scriptContents);
        final Path imagePath = Paths.get(workingFolder,
                TMP_FILE_PREFIX + UUID.randomUUID().toString() + "." + screenshotType.toString().toLowerCase());

        // Execute the process
        List<String> processParams = new ArrayList<String>();
        processParams.add(executable);
        processParams.add(scriptPath.toAbsolutePath().toString()); // 0
        processParams.add(imagePath.toAbsolutePath().toString()); // 1
        processParams.addAll(Arrays.asList(params)); // 2 - N

        final ProcessBuilder processBuilder = new ProcessBuilder(processParams);
        processBuilder.directory(new File(workingFolder));

        log.error("Command: {}", processBuilder.command());

        final Process process = processBuilder.start();
        process.waitFor();
        process.getInputStream().close();
        process.getOutputStream().close();
        process.getErrorStream().close();

        // Get the Image as inputstream
        InputStream inputStream = Files.newInputStream(imagePath);

        // Create the script file
        Files.deleteIfExists(scriptPath);
        Files.deleteIfExists(imagePath);

        return inputStream;
    }


    private Path createScript(String workingFolder, String scriptContents) throws IOException {
        final Path path = Files.createTempFile(Paths.get(workingFolder), TMP_FILE_PREFIX, ".js");

        final BufferedWriter bw = Files.newBufferedWriter(path, Charset.defaultCharset());
        bw.write(scriptContents);
        bw.close();

        return path;
    }

    @Activate
    protected void activate(Map<String, Object> config) {
        this.workingFolder = PropertiesUtil.toString(config.get(PROP_WORKING_FOLDER), DEFAULT_WORKING_FOLDER);
        this.executable = PropertiesUtil.toString(config.get(PROP_EXECUTABLE), DEFAULT_EXECUTABLE);
    }
}


