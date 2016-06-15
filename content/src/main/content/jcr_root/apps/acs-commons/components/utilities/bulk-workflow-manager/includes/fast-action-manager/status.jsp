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

<%-- Status Summary --%>
<h2>Bulk Workflow Execution Summary</h2>

<div style="width: 48%; padding-right: 2%; float: left;">
    <table class="coral-Table coral-Table--bordered">
        <tbody>
            <tr class="coral-Table-row">
                <td class="coral-Table-cell">Runner</td>
                <td class="coral-Table-cell">Synthetic Workflow w/ FAM</td>
            </tr>

            <tr class="coral-Table-row">
                <td class="coral-Table-cell">Status</td>
                <td class="coral-Table-cell">
                    {{ data.status.status }}
                    <span ng-show="data.status.subStatus && data.status.subStatus !== 'SLEEPING'"> ( {{ data.status.subStatus }} )</span>
            </tr>

            <tr class="coral-Table-row">
                <td class="coral-Table-cell">Completed</td>
                <td class="coral-Table-cell">{{ data.status.completeCount }}</td>
            </tr>

            <tr class="coral-Table-row">
                <td class="coral-Table-cell">Failed</td>
                <td class="coral-Table-cell">{{ data.status.failCount }}</td>
            </tr>

            <tr class="coral-Table-row">
                <td class="coral-Table-cell">Remaining</td>
                <td class="coral-Table-cell">{{ data.status.remainingCount }}</td>
            </tr>

            <tr class="coral-Table-row">
                <td class="coral-Table-cell">Total</td>
                <td class="coral-Table-cell">{{ data.status.totalCount }}</td>
            </tr>

            <tr class="coral-Table-row"
                ng-show="data.status.startedAt">
                <td class="coral-Table-cell">CPU Usage</td>
                <td class="coral-Table-cell">{{ data.status.systemStats.cpu }} / {{ data.status.systemStats.maxCpu }}</td>
            </tr>

            <tr class="coral-Table-row"
                ng-show="data.status.startedAt">
                <td class="coral-Table-cell">Memory Usage</td>
                <td class="coral-Table-cell">{{ data.status.systemStats.mem }} / {{ data.status.systemStats.maxMem }}</td>
            </tr>

        </tbody>
    </table>
</div>

<div style="width: 48%; padding-left: 2%; float: left;">
    <table class="coral-Table coral-Table--bordered">
    <tbody>
        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Query Type</td>
            <td class="coral-Table-cell" style="text-transform: capitalize;">{{ data.status.queryType }}</td>
        </tr>

        <tr class="coral-Table-row" ng-hide="data.status.queryType === 'list'">
            <td class="coral-Table-cell">Query Statement</td>
            <td class="coral-Table-cell">{{ data.status.queryStatement }}</td>
        </tr>

        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Workflow Model</td>
            <td class="coral-Table-cell">{{ data.status.workflowModel }}</td>
        </tr>


        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Save Interval</td>
            <td class="coral-Table-cell">{{ data.status.interval }}</td>
        </tr>


        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Update Granularity</td>
            <td class="coral-Table-cell">{{ data.status.batchSize }}</td>
        </tr>


        <tr class="coral-Table-row"
            ng-show="data.status.startedAt">
            <td class="coral-Table-cell">Started At</td>
            <td class="coral-Table-cell">{{ data.status.startedAt }}</td>
        </tr>

        </tbody>
    </table>
</div>
