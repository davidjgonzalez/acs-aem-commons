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


<%-- Status Summary --%>
<section class="coral-Well">

    <h4 acs-coral-heading>Bulk workflow execution summary</h4>

    <div class="acs-column-50-50">
        <ul acs-coral-list>
            <li>Status: <span style="text-transform: capitalize;">{{ data.status.status }}</span></li>
            <li>Total: {{ data.status.totalCount }}</li>
            <li>Complete: {{ data.status.completedCount }}</li>
            <li>Failed: {{ data.status.failedCount }}</li>
            <li>Remaining: {{ data.status.remainingCount }}</li>

            <li ng-show="data.status.startedAt">Started At: {{ data.status.startedAt }}</li>
            <li ng-show="data.status.stoppedAt && !data.status.completedAt">Stopped At: {{ data.status.stoppedAt }}</li>
            <li ng-show="data.status.completedAt">Competed At: {{ data.status.completedAt }}</li>
        </ul>
    </div>

    <div class="acs-column-50-50">
        <ul acs-coral-list>
            <li>Batch size: {{ data.status.batchSize }}</li>
            <li>Workflow timeout: {{ data.status.timeout }} seconds</li>
            <li>Process interval: {{ data.status.interval }} seconds</li>
            <li>Workflow model: {{ data.status.workflowModel }}</li>
            <li>Purge workflow: {{ data.status.purgeWorkflow }}</li>
        </ul>
    </div>

    <br clear="all"/>
</section>


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
            ng-show="data.status.status === 'RUNNING'"
            class="warning">Stop Bulk Workflow</button>

    <button ng-click="resume()"
            role="button"
            class="coral-Button coral-Button--primary"
            ng-show="data.status.status.indexOf('STOPPED') === 0"
            style="float: left;"
            class="primary">Resume Bulk Workflow</button>

    <div   style="margin-left: 12.5rem; line-height: 2.5rem"
           ng-show="data.status.status.indexOf('STOPPED') === 0">
        
        Update batch interval to

        <input type="text"
               class="coral-Form-field coral-Textfield"
               ng-required="false"
               ng-model="form.interval"
               placeholder="{{ form.interval }}"/>
        
        seconds.
    </div>
</div>

<%-- Status Table --%>

<section  ng-show="data.status.status === 'RUNNING'">
    <hr/>

    <h3 acs-coral-heading>Current batch</h3>

    <div class="acs-section" style="line-height: 2.5rem;">
         Refresh status every
        <input type="text"
               class="coral-Form-field coral-Textfield"
               ng-blur="updatePollingInterval(form.pollingInterval)"
               ng-model="form.pollingInterval"
               placeholder="{{ dfault.pollingInterval }}"/> seconds, or

         <button ng-click="status(true)"
                 role="button"
                 class="coral-Button inline-button">Refresh now</button>
    </div>

    <table class="coral-Table" current-batch-table>
        <thead>
            <tr class="coral-Table-row">
                <th class="coral-Table-headerCell status-col">Status</th>
                <th class="coral-Table-headerCell">Payload</th>
            </tr>
        </thead>
        <tbody>
            <tr class="coral-Table-row" ng-repeat="item in data.status.activePayloads ">
                <td class="coral-Table-cell {{ item.status }}">{{ item.status || 'NOT STARTED' }}</td>
                <td class="coral-Table-cell">{{ item.path }}</td>
            </tr>
        </tbody>
    </table>
</section>
