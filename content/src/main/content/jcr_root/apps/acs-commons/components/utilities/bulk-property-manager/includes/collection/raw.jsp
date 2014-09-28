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

<div ng-show="form.queryMode === 'raw'">
    <div class="form-row">
        <h4>JCR-SQL2 Query</h4>

        <span>
            <textarea
                    ng-model="form.raw.query"
                    placeholder="SELECT * FROM [cq:Page] WHERE ISDESCENDANTNODE([/content])"></textarea>

            <div class="instructions">
                Example: SELECT * FROM [cq:Page] WHERE ISDESCENDANTNODE([/content])
                <br/>
                Please ensure that this query is correct prior to submitting form as it will collect the resources
                for processing which can be an expensive operation for property management processes.
            </div>
        </span>
    </div>

    <div class="form-row">
        <h4>Relative Path</h4>

        <span>
           <input ng-model="form.raw.relativePath"
                  type="text"
                  placeholder="jcr:content/foo"/>
        </span>
    </div>
</div>