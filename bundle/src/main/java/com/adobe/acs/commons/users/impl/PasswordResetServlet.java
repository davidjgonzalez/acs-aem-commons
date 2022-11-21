package com.adobe.acs.commons.users.impl;

import com.google.gson.JsonObject;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;

@Component(
        service = {Servlet.class}
)
@SlingServletResourceTypes(
        resourceTypes = "acs-commons/components/utilities/local-user-password-reset",
        methods = "POST",
        extensions = "json",
        selectors = "update")
public class PasswordResetServlet extends SlingAllMethodsServlet {
    private final static Logger log = LoggerFactory.getLogger(PasswordResetServlet.class);

    @Override
    protected final void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
            ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        // check if username and password are provided
        if (username == null || password == null) {
            response.sendError(400, "Username and password must be provided");
            return;
        }

        gilog.info("Resetting password for user " + username);

        UserManager userManager = request.getResourceResolver().adaptTo(UserManager.class);
        try {
            final User user = userManager.getAuthorizable(username, User.class);
            user.changePassword(password);
            response.getWriter().write(getSuccessJson(username));

        } catch (RepositoryException | IOException e) {
            log.error("Error updating password for user: " + username, e);

            response.setStatus(500);
            response.getWriter().write(getFailureJson(username, e.getMessage()));
        }
    }

    private String getSuccessJson(String username) {
        JsonObject json = new JsonObject();

        json.addProperty("success", true);
        json.addProperty("username", username);

        return json.getAsString();
    }

    private String getFailureJson(String username, String message) {
        JsonObject json = new JsonObject();

        json.addProperty("success", false);
        json.addProperty("username", username);
        json.addProperty("message", message);

        return json.getAsString();
    }
}
