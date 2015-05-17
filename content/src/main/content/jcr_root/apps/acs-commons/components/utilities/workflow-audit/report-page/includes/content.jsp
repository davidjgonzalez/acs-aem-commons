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
  --%>
<%@include file="/libs/foundation/global.jsp" %><%
%><%@page session="false" %><%

    pageContext.setAttribute("resourcePath", resourceResolver.map(resource.getPath()));

%><div ng-controller="ReportCtrl"
     ng-init="app.uri = '${resourcePath}.report.json'; load();">

    <table st-table="displayedCollection"
           st-safe-src="rowCollection"
           class="table table-striped data" style="width: 100%;">
        <thead>
            <tr>
                <th colspan="5">
                    <input st-search="" class="form-control" placeholder="Search..." type="text" style="width: 100%;"/>
                </th>
            </tr>
            <tr>
                <th>Initiator</th>
                <th>Workflow</th>
                <th>Payload</th>
                <th>Status</th>
                <th></th>
            </tr>
        </thead>
        <tbody>
            <tr ng-repeat="row in displayedCollection">
                <td>{{row.initiator}}</td>
                <td>{{row.modelTitle}}</td>
                <td>{{row.payload}}</td>
                <td>{{row.status}}</td>
                <td><a x-cq-linkchecker="skip"
                       target="_blank"
                       href="${resource.path}.details.html{{row.path}}">View</a></td>
            </tr>
        </tbody>
        <tfoot>
            <tr>
                <td colspan="5" class="text-center">
                    <div st-pagination="" st-items-by-page="itemsByPage" st-displayed-pages="7"></div>
                </td>
            </tr>
        </tfoot>
    </table>
</div>
