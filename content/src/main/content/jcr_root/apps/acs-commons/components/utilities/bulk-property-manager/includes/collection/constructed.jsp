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

<div ng-show="form.queryMode === 'constructed'">

    <div class="form-row">
        <h4>Collection Mode</h4>

        <span>
            <div class="selector">
                <label><input
                        ng-model="form.constructed.collectionMode"
                        type="radio"
                        value="traversal"><span>Traversal</span></label>
                <label><input
                      ng-model="form.constructed.collectionMode"
                      type="radio"
                      value="query"><span>Query</span></label>
            </div>
        </span>
    </div>

    <div class="form-row">
        <h4>Path</h4>

        <span>
           <input ng-model="form.constructed.path"
                  type="text"
                  placeholder="/content"/>
        </span>
    </div>

    <div class="form-row">
        <h4>Node Type</h4>

        <span>
           <input ng-model="form.constructed.nodeType"
                  type="text"
                  placeholder="nt:unstructured"/>
        </span>
    </div>



    <div class="form-row">
        <h4>Properties Operand</h4>

            <span>
                <select
                        ng-required="true"
                        ng-model="form.constructed.propertiesOperand">
                    <option value="OR">OR</option>
                    <option value="AND">AND</option>
                </select>
            </span>
    </div>

    <div class="form-row">
        <h4>Properties</h4>

        <span>
            <table class="data" style="width: 75%">
                <thead>
                    <tr>
                        <th>Property Name</th>
                        <th>Property Value</th>
                        <th class="action-button-col">&nbsp;</th>
                    </tr>
                </thead>
                <tbody>
                    <tr ng-repeat="property in form.constructed.properties">
                        <td><input ng-model="property.name"
                                   type="text"
                                   placeholder="Property Name"/></td>
                        <td><input ng-model="property.value"
                                   type="text"
                                   placeholder="Property Value"/></td>
                        <td class="action-button-col"><a
                                ng-show="form.constructed.properties.length > 1"
                                ng-click="form.constructed.properties.splice(form.constructed.properties.indexOf(property), 1)"
                                href="#"
                                class="icon-minus-circle"></a></td>
                    </tr>
                </tbody>
                <tfoot>
                    <tr>
                        <td colspan="2">&nbsp;</td>
                        <td class="action-button-col"><a
                                ng-click="form.constructed.properties.push({ name: '', value: '' })"
                                href="#"
                                class="icon-add-circle">Add</a></td>
                    </tr>

                </tfoot>
            </table>
        </span>
    </div>
</div>