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

<div ng-controller="FindAndReplaceCtrl"
     ng-show="form.mode === 'find-and-replace'">

    <div class="form-row">
        <h4>From Property</h4>

        <span>
            <input type="text"
                   ng-required="true"
                   ng-model="form.findAndReplace.find"
                   placeholder="The value to find"/>
        </span>
    </div>

    <div class="form-row">
        <h4>To Property</h4>

        <span>
            <input type="text"
                   ng-required="true"
                   ng-model="form.findAndReplace.replace"
                   placeholder="The replacement string"/>
        </span>
    </div>

    <div class="form-row">
        <div class="form-left-cell">&nbsp;</div>

        <cq:include script="dry-run.jsp"/>

        <button ng-click="findAndReplace()"
                class="submit-button primary">Find &amp; Replace</button>
    </div>
</div>