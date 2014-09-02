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

    pageContext.setAttribute("currentTime", System.currentTimeMillis());

%><%@page session="false" %><%

%><div id="acs-commons-bulk-property-manager-app"
        ng-controller="MainCtrl"
        ng-init="app.resource='${resource.path}';">

    <h1>Bulk Property Manager</h1>

    <p></p>

    <div class="form">

        <div class="form-row">
            <h4>Query Mode</h4>

            <span>
                <div class="selector">
                    <label><input
                            ng-model="form.queryMode"
                            value="constructed"
                            type="radio"><span>Constructed Query</span></label>
                    <label><input
                            ng-model="form.queryMode"
                            value="raw"
                            type="radio"><span>Raw Query</span></label>
                </div>
            </span>
        </div>

        <cq:include script="includes/collection-raw.jsp"/>
        <cq:include script="includes/collection-constructed.jsp"/>

        <div class="form-row">
            <h4>Mode</h4>

            <span>
                <select
                        ng-required="true"
                        ng-model="form.mode">
                    <option value="">Select</option>
                    <option value="add">Add</option>
                    <option value="remove">Remove</option>
                    <option value="copy">Copy</option>
                    <option value="move">Move</option>
                    <option value="find-and-replace">Find &amp; Replace</option>
                </select>
            </span>
        </div>

        <cq:include script="includes/add.jsp"/>
        <cq:include script="includes/copy.jsp"/>
        <cq:include script="includes/remove.jsp"/>
        <cq:include script="includes/move.jsp"/>
        <cq:include script="includes/find-and-replace.jsp"/>
    </div>

    <form ng-show="!form.mode"
          method="post"
          action="${resource.path}/bulk-property-dry-run.csv">

        <div class="form-row">
            <div class="form-left-cell">&nbsp;</div>
            <span>
                <input type="hidden"
                       name="params"
                       value="{{ form }}"/>

                <button type="submit"
                        class="submit-button">Dry Run</button>
            </span>
        </div>
    </form>

</div>

<cq:includeClientLib js="acs-commons.bulk-property-manager.app"/>

<%-- Register angular app; Decreases chances of collisions w other angular apps on the page (ex. via injection) --%>
<script type="text/javascript">
    angular.bootstrap(document.getElementById('acs-commons-bulk-property-manager-app'),
        ['bulkPropertyManagerApp']);
</script>
