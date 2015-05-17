<%--
  ~ #%L
  ~ ACS AEM Commons Bundle
  ~ %%
  ~ Copyright (C) 2013 Adobe
  ~ %%
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  ~ #L%
  --%><%@include file="/libs/foundation/global.jsp" %><%
%><%@page session="false"
          import="org.apache.sling.api.resource.Resource,
                 org.apache.sling.api.resource.ValueMap,
                 com.adobe.acs.commons.workflow.audit.WorkflowAuditReportHelper"%><%

    WorkflowAuditReportHelper reportHelper = sling.getService(WorkflowAuditReportHelper.class);

    String suffix = slingRequest.getRequestPathInfo().getSuffix();
    Resource wfResource = resourceResolver.getResource(suffix);
    ValueMap wfProperties = wfResource.adaptTo(ValueMap.class);

    pageContext.setAttribute("workflow", wfProperties);
    pageContext.setAttribute("children", wfResource.listChildren());
    //pageContext.setAttribute("workItems", reportHelper.getWorkItems(wfResource));

%>

<h3>Workflow Details</h3>

<ul>
    <li>Workflow: ${workflow.modelTitle} (v${workflow.modelVersion})</li>
    <li>Initiator: ${workflow.initiator}</li>
    <li>Payload: ${workflow.payload}</li>
    <li>Status: ${workflow.status}</li>
    <li>Start Time: <fmt:formatDate value="${workflow.startedAt.time}" pattern="yy-MMM-dd"/></li>

    <c:if test="${not empty workflow.endTime}">
        <li>End Time: <fmt:formatDate value="${workflow.endedAt.time}" pattern="yy-MMM-dd"/></li>
    </c:if>
</ul>

<hr/>

<h4>Workflow Step History</h4>

<c:forEach var="child" items="${children}">
    <cq:include path="${child.path}" resourceType="${component.resourceType}/work-item" />
    <hr/>
</c:forEach>
