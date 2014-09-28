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

<div ng-controller="ResultsCtrl"
        ng-init="getDryRunResults()">

    <p>Download the results from Bulk Property Dry Runs below.</p>

    <table class="data" style="width: 100%">
        <thead>
            <tr>
                <th>Operation</th>
                <th class="num-col">Success</th>
                <th class="num-col">Error</th>
                <th class="num-col">Noop</th>
                <th class="num-col">Total</th>
                <th>Created</th>
                <th class="action-button-col">&nbsp;</th>
                <th class="action-button-col">&nbsp;</th>
            </tr>
        </thead>
        <tbody>
            <tr ng-repeat="result in dryRunResults | orderBy : 'result.name * 1' : true ">
                <td>{{ result.operation }}</td>
                <td class="num-col">{{ result.success }}</td>
                <td class="num-col">{{ result.error }}</td>
                <td class="num-col">{{ result.noop }}</td>
                <td class="num-col">{{ result.total }}</td>
                <td>{{ result.createdAt }} by {{ result.createdBy }}</td>
                <td class="action-button-col"><a x-cq-linkchecker="skip"
                       class="icon-download "
                       ng-show="result.total > 0"
                       href="{{ result.filePath }}"
                       target="_blank">{{ result.fileName }}></a></td>
                <td class="action-button-col"><a ng-click="removeResult(result)"
                        class="icon-delete"></a></td>
            </tr>
            <tr ng-show="dryRunResults && dryRunResults.length < 1">
                <td colspan="8">There are no dry run results.</td>
            </tr>
        </tbody>
    </table>

</div>