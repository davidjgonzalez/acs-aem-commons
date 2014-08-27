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

<div ng-controller="AddCtrl"
     ng-show="form.mode === 'add'">

    <div class="form-row">
        <h4>Property Name</h4>

        <span>
            <input type="text"
                   ng-required="true"
                   ng-model="form.add.name"
                   placeholder="The property name to add"/>
        </span>
    </div>

    <div class="form-row">
        <h4>Property Type</h4>

        <span>
            <select
                    ng-required="true"
                    ng-model="form.add.type">
                <option value="String">String</option>
                <option value="Long">Long</option>
                <option value="Date">Date</option>
                <option value="Boolean">Boolean</option>
            </select>
        </span>
    </div>

    <div class="form-row">
        <h4>Property Value</h4>

        <span>
            <input type="text"
                   ng-required="true"
                   ng-model="form.add.value"
                   placeholder="The property value to add"/>
        </span>
    </div>

    <div class="form-row">
        <h4>Overwrite</h4>

        <span>
            <label><input type="checkbox"
                          ng-model="form.add.overwrite"
                          checked><span></span></label>
        </span>
    </div>


    <div class="form-row">
        <div class="form-left-cell">&nbsp;</div>

        <cq:include script="dry-run.jsp"/>

        <button ng-click="add()"
                class="submit-button primary">Add Property</button>
    </div>
</div>