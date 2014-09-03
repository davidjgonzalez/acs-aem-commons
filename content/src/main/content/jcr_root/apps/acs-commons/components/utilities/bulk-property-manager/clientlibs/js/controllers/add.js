/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2013 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

/*global angular: false, JSON: false, bulkPropertyManagerApp: false */

bulkPropertyManagerApp.controller('AddCtrl', function ($scope, $http, $timeout) {

    $scope.add = function () {
        $scope.app.running = true;

        $http({
            method: 'POST',
            url: encodeURI($scope.app.resource + '.add.json'),
            data: 'params=' + JSON.stringify($scope.form),
            headers: {'Content-Type': 'application/x-www-form-urlencoded'}
        }).
            success(function (data, status, headers, config) {
                $scope.result = data;
                $scope.app.running = false;
                //$scope.addNotification('info', 'INFO', 'Copied properties successfully. ');
            }).
            error(function (data, status, headers, config) {
                $scope.app.running = false;
                $scope.error = data;
                //$scope.addNotification('error', 'ERROR', data);
            });
    };
});
