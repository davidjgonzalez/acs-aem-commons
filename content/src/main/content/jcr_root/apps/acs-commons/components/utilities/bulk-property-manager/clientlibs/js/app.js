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

/*global angular: false, JSON: false */

angular.module('acs-commons-bulk-property-manager-app', [])
    .controller('MainCtrl', ['$scope', '$http', '$timeout', function ($scope, $http, $timeout) {

    $http.defaults.headers.post['Content-Type'] = 'application/x-www-form-urlencoded;charset=utf-8';

    $scope.app = {
        resource: '',
        running: false
    };

    $scope.notifications = [];

    $scope.form = {
        queryMode: 'constructed',
        raw: {},
        constructed: {
            collectionMode: 'traversal',
            properties: [
                { name: '', value: ''}
            ],
            propertiesOperand: 'OR'
        },
        add: {},
        copy: {},
        remove: {},
        move: {},
        findAndReplace: {}
    };

    $scope.results = [];
    $scope.dryRunResults = [];


    $scope.addNotification = function (type, title, message) {
        var timeout = 30000;

        if((typeof message === 'object'
                && !Array.isArray(message)
                && message !== null)) {

            message = $scope.responseToString(message);
        }

        $scope.notifications.push({
            type: type,
            title: title,
            message: message
        });

        $timeout(function() {
            $scope.notifications.shift();
        }, timeout);
    };


    $scope.responseToString = function(data) {
        return data.message
            + '[ Operation: ' + data.operation + ' ] '
            + '[ Success: ' + data.success + ' ] '
            + '[ Error: ' + data.error + ' ] '
            + '[ Noop: ' + data.noop + ' ] '
            + '[ Total: ' + data.total + ' ] ';
    };

}]);


