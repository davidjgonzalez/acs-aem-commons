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

<form
        novalidate
        name="params"
        ng-submit="start(params.$valid)"
        class="acs-form coral-Form coral-Form--vertical">

    <section class="coral-Form-fieldset">
        <h3 class="coral-Form-fieldset-legend">Bulk workflow setup</h3>

        <div class="coral-Form-fieldwrapper">
            <label class="coral-Form-fieldlabel">Bulk Workflow Runner</label>

            <select
                    class="acs-select"
                    name="runnerType"
                    ng-required="true"
                    ng-model="form.runnerType"
                    ng-options="runnerType.value as runnerType.label for runnerType in formOptions.runnerTypes">
            </select>
        </div>

        <div class="coral-Form-fieldwrapper">
            <label class="coral-Form-fieldlabel">Query Type</label>

            <select
                    class="acs-select"
                    name="queryTpe"
                    ng-required="true"
                    ng-model="form.queryType"
                    ng-options="queryType.value as queryType.label for queryType in formOptions.queryTypes">
            </select>
        </div>

        <div class="coral-Form-fieldwrapper">
            <label class="coral-Form-fieldlabel">Query</label>

            <textarea
                    class="coral-Form-field coral-Textfield coral-Textfield--multiline"
                    name="queryStatement"
                    ng-required="true"
                    ng-model="form.queryStatement"></textarea>

            <span class="coral-Form-fieldinfo coral-Icon coral-Icon--infoCircle coral-Icon--sizeS" data-init="quicktip" data-quicktip-type="info" data-quicktip-arrow="right"
                data-quicktip-content="Ensure that this query is correct prior to submitting form as it will collect the resources for processing which can be an expensive operation for large bulk workflow processes. Example: SELECT * FROM [cq:Page] WHERE ISDESCENDANTNODE([/content])"></span>
        </div>

        <div class="coral-Form-fieldwrapper">
            <label class="coral-Form-fieldlabel">Search Node Relative Path</label>

            <input
               type="text"
               class="coral-Form-field coral-Textfield"
               ng-model="form.relativePath"
               placeholder="Relative path to append to search results [ Optional ]"/>
            <span class="coral-Form-fieldinfo coral-Icon coral-Icon--infoCircle coral-Icon--sizeS" data-init="quicktip" data-quicktip-type="info" data-quicktip-arrow="right"
                data-quicktip-content="This can be used to select otherwise difficult to search for resources. Examples: jcr:content/renditions/original OR ../renditions/original"></span>
        </div>

        <div class="coral-Form-fieldwrapper">
            <label class="coral-Form-fieldlabel">Workflow Model</label>

            <select
                    class="acs-select"
                    name="workflowModel"
                    ng-required="true"
                    ng-model="form.workflowModel"
                    ng-options="workflowModel.value as workflowModel.label for workflowModel in formOptions.workflowModels">
            </select>
        </div>

        <div class="coral-Form-fieldwrapper">
            <label class="coral-Form-fieldlabel">Batch Size</label>

            <input name="batchSize"
                   type="number"
                   min="2"
                   class="coral-Form-field coral-Textfield"
                   ng-pattern="/(^[2-9]\d*)|(^[1-9]\d+)/"
                   ng-model="form.batchSize"
                   placeholder="# of payloads to process at once [ Default: 10 ]"/>
            <span class="coral-Form-fieldinfo coral-Icon coral-Icon--infoCircle coral-Icon--sizeS" data-init="quicktip" data-quicktip-type="info" data-quicktip-arrow="right"
                data-quicktip-content="Batch size must be greater than 1"></span>
        </div>

        <div ng-show="form.runnerType === 'com.adobe.acs.commons.workflow.bulk.execution.impl.runners.AEMWorkflowRunnerImpl'">
            <%@include file="form-aem-workflow.jsp"%>
        </div>

        <div ng-show="form.runnerType === 'com.adobe.acs.commons.workflow.bulk.execution.impl.runners.SyntheticWorkflowRunnerImpl'">
            <%@include file="form-synthetic-workflow.jsp"%>
        </div>

        <hr/>

        <div class="coral-Form-fieldwrapper"
                ng-show="data.status.status === 'NOT_STARTED'">

            <button type="submit"
                    role="button"
                    class="coral-Button coral-Button--primary"
                    ng-show="params.$valid && !params.$pristine"
                    class="primary">Start Bulk Workflow</button>

            <button type="submit"
                    role="button"
                    class="coral-Button coral-Button--primary"
                    ng-show="params.$invalid || params.$pristine"
                    disabled>Start Bulk Workflow</button>

        </div>
    </section>
</form>