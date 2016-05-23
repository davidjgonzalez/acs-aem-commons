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

<div class="coral-Form-fieldwrapper">
    <label class="coral-Form-fieldlabel">Batch Throttle (in seconds)</label>

    <input name="throttle"
           type="number"
           min="0"
           class="coral-Form-field coral-Textfield"
           ng-pattern="/\d+/"
           ng-model="form.throttle"
           placeholder="in seconds [ Default: 10 ]"/>
            <span class="coral-Form-fieldinfo coral-Icon coral-Icon--infoCircle coral-Icon--sizeS" data-init="quicktip" data-quicktip-type="info" data-quicktip-arrow="right"
                  data-quicktip-content="Number of seconds to wait before processing the next batch. 0 to disable."></span>
</div>
