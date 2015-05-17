package com.adobe.acs.commons.workflow.audit.impl;


import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.AbstractResourceVisitor;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Date;

@SlingServlet(
        label = "ACS AEM Commons - Workflow Audit Data",
        resourceTypes = { "acs-commons/components/utilities/workflow-audit/report-page" },
        selectors = { "report" },
        extensions = { "json "},
        methods = { "GET" }
)
public class WorkflowAuditDataServlet extends SlingSafeMethodsServlet {
    private Logger log = LoggerFactory.getLogger(WorkflowAuditDataServlet.class);

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        final JSONArray table = new JSONArray();

        final Resource root = request.getResourceResolver().getResource(Constants.PATH_WORKFLOW_AUDIT);
        final AuditVisitor auditVisitor = new AuditVisitor(table);

        log.debug("Building Workflow Audit Report JSON from [ {} ]", root.getPath());

        auditVisitor.accept(root);

        response.getWriter().print(table.toString());
    }


    private class AuditVisitor extends AbstractResourceVisitor {
        private final Logger log = LoggerFactory.getLogger(AuditVisitor.class);

        private final JSONArray table;

        private JSONObject row = null;

        public AuditVisitor(JSONArray table) {
            this.table = table;
        }

        public JSONArray getTable() {
            return this.table;
        }

        @Override
        public final void accept(final Resource resource) {
            final Node node = resource.adaptTo(Node.class);

            try {
                if(node != null && node.isNodeType(Constants.NT_SLING_ORDERED_FOLDER)) {
                    log.debug("Accepting [ {} ]", resource.getPath());
                    super.accept(resource);
                }
            } catch (RepositoryException e) {
                log.error("Could not check Node Type for [ {} ]", resource.getPath());
            }
        }

        @Override
        protected void visit(final Resource resource) {
            log.debug("Visiting resource [ {} ]", resource.getPath());

            final ValueMap properties = resource.adaptTo(ValueMap.class);

            try {
                if (resource.isResourceType(Constants.RT_WORKFLOW_INSTANCE_AUDIT)) {
                    row = new JSONObject();

                    // New Row
                    String payload = properties.get("payload", String.class);


                    row.put("path", resource.getPath());
                    row.put("payload", properties.get("payload", "?"));
                    row.put("payloadContent", properties.get("friendlyPath", payload));
                    row.put("payloadTitle", properties.get("title", "?"));
                    row.put("status", properties.get("status", "?"));
                    row.put("modelTitle", properties.get("modelTitle", "?"));
                    row.put("modelVersion", properties.get("modelVersion", "1.0"));
                    row.put("initiator", properties.get("initiator", "?"));

                    if (properties.get("startedAt", Date.class) != null) {
                        row.put("startedAt", properties.get("startedAt", Date.class));
                    }

                    if (properties.get("endedAt", Date.class) != null) {
                        row.put("endedAt", properties.get("endedAt", Date.class));
                    }

                    log.debug("Adding row: {}", row.toString());

                    table.put(row);

                }
            } catch (JSONException e) {
                log.error("Cannot build audit JSON response", e);
            }
        }
    }
}