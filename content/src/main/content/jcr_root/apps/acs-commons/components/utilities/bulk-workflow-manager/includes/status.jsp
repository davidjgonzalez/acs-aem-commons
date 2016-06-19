<%--
  ~ #%L
  ~ ACS AEM Commons Bundle
  ~ %%
  ~ Copyright (C) 2016 Adobe
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
<%-- Status Boxes --%>
<div    ng-show="data.status.status === 'RUNNING'"
        acs-coral-alert
        data-alert-type="info"
        data-alert-size="large"
        data-alert-title="Running">
    Please be patient while Bulk Workflow executes.
</div>

<div    ng-show="data.status.status === 'STOPPED'"
        acs-coral-alert
        data-alert-type="notice"
        data-alert-size="large"
        data-alert-title="Stopped">
    The execution of this bulk workflow process was stopped.
    Press the &quot;Resume Bulk Worklfow&quot; button below to resume bulk workflow processing.
</div>

<div    ng-show="data.status.status === 'COMPLETED'"
        acs-coral-alert
        data-alert-type="success"
        data-alert-size="large"
        data-alert-title="Complete">
    The execution of this bulk run is complete. Please review the
    <a target="_blank" href="/libs/cq/workflow/content/console.html">workflow history</a>
    for any unsuccessful Workflow executions.
    <br/>
    To execute another bulk workflow execution, create a new Bulk Workflow Manager page.
</div>

<div ng-show="isWorkflow()">
    <%@include file="aem-workflow/status.jsp"%>
</div>

<div ng-show="isSynthetic()">
    <%@include file="synthetic-workflow/status.jsp"%>
</div>

<div ng-show="isFAM()">
    <%@include file="fast-action-manager/status.jsp"%>
</div>

<%-- Progress Bar --%>
<div class="coral-Progress acs-section acs-progress-bar"
     ng-show="data.status.percentComplete || data.status.percentComplete === 0">
    <div class="coral-Progress-bar">
        <div class="coral-Progress-status"
             style="width: {{ data.status.percentComplete }}%;"></div>
    </div>
    <label class="coral-Progress-label">{{ data.status.percentComplete }}%</label>
</div>


<%-- Controls --%>

<div class="acs-section">

    <button ng-click="stop()"
            role="button"
            class="coral-Button coral-Button--warning"
            ng-show="data.status.status === 'RUNNING' && data.status.subStatus !== 'STOPPING'"
            class="warning">Stop Bulk Workflow</button>

    <button role="button"
            class="coral-Button coral-Button--disabled"
            ng-show="data.status.subStatus === 'STOPPING'"
            class="warning">Stopping...</button>

    <button ng-click="resume()"
            role="button"
            class="coral-Button coral-Button--primary"
            ng-show="data.status.status === 'STOPPED'"
            style="float: left;"
            class="primary">Resume Bulk Workflow</button>

    <div   style="margin-left: 12.5rem; line-height: 2.5rem"
           ng-show="data.status.status === 'STOPPED'">

        <div ng-show="isWorkflow()">
            <%@include file="aem-workflow/interval-update.jsp"%>
        </div>

    </div>
</div>

<%-- Running Payloads Table --%>
<section  ng-show="data.status.status === 'RUNNING'">
    <hr/>

    <div class="acs-section" style="line-height: 2.5rem;">
        Refresh status every
        <input type="text"
               style="width: 5rem"
               class="coral-Form-field coral-Textfield"
               ng-blur="updatePollingInterval(form.pollingInterval)"
               ng-model="form.pollingInterval"
               placeholder="{{ dfault.pollingInterval }}"/> seconds, or

        <button ng-click="status(true)"
                role="button"
                class="coral-Button inline-button">Refresh now</button>
    </div>


    <div ng-show="isWorkflow()">
        <%@include file="aem-workflow/status-table.jsp"%>
    </div>

    <div ng-show="isSynthetic()">
        <%@include file="synthetic-workflow/status-table.jsp"%>
    </div>

    <div ng-show="isFAM()">
        <%@include file="fast-action-manager/status-table.jsp"%>
    </div>
</section>

<!-- Failure Table -->
<section>
    <%@include file="failures-table.jsp"%>
</section>
