<%-- Status Summary --%>
<h2>Bulk Workflow Execution Summary</h2>

<div style="width: 48%; padding-right: 2%; float: left;">
    <table class="coral-Table coral-Table--bordered">
        <tbody>
        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Status</td>
            <td class="coral-Table-cell" style="text-transform: capitalize;">{{ data.status.status }}</td>
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
        </tbody>
    </table>
</div>

<div style="width: 48%; padding-left: 2%; float: left;">
    <table class="coral-Table coral-Table--bordered">
        <tbody>
        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Batch Size</td>
            <td class="coral-Table-cell">{{ data.status.batchSize }}</td>
        </tr>

        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Batch Timeout</td>
            <td class="coral-Table-cell">{{ data.status.timeout }} seconds</td>
        </tr>

        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Batch Interval</td>
            <td class="coral-Table-cell">{{ data.status.interval }} seconds</td>
        </tr>


        <tr class="coral-Table-row">
            <td class="coral-Table-cell">Workflow Model</td>
            <td class="coral-Table-cell">{{ data.status.workflowModel }}</td>
        </tr>

        <tr class="coral-Table-row"
            ng-show="data.status.startedAt">
            <td class="coral-Table-cell">Started At</td>
            <td class="coral-Table-cell">{{ data.status.startedAt }}</td>
        </tr>

        <tr class="coral-Table-row"
            ng-show="data.status.stoppedAt && !data.status.completedAt">
            <td class="coral-Table-cell">Stopped At</td>
            <td class="coral-Table-cell">{{ data.status.stoppedAt }}</td>
        </tr>

        <tr class="coral-Table-row"
            ng-show="data.status.completedAt">
            <td class="coral-Table-cell">Completed At</td>
            <td class="coral-Table-cell">{{ data.status.completedAt }}</td>
        </tr>

        </tbody>
    </table>
</div>

<div style="clear: both; margin-bottom: 1rem;"></div>
