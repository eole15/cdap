angular.module(PKG.name + '.commons')
  .directive('myViewQueries', function () {

    return {
      restrict: 'E',
      scope: {
        panel: '='
      },
      templateUrl: 'view-queries/view-queries.html',
      controller: function ($scope, MyDataSource, $state) {
        var dataSrc = new MyDataSource($scope);
        $scope.queries = [];

        $scope.getQueries = function() {
          dataSrc
            .request({
              _cdapNsPath: '/data/explore/queries',
              method: 'GET'
            })
            .then(function (queries) {

              $scope.queries = queries;

              angular.forEach($scope.queries, function(query) {
                query.isOpen = false;
              });
            });
        };

        $scope.getQueries();


        $scope.responses = {};

        $scope.fetchResult = function(query) {
          // Close other accordion
          angular.forEach($scope.queries, function(q) {
            q.isOpen = false;
          });

          query.isOpen = !query.isOpen;

          if (query.isOpen) {
            $scope.responses.request = query;

            // request schema
            dataSrc
              .request({
                _cdapPath: '/data/explore/queries/' +
                              query.query_handle + '/schema'
              })
              .then(function (result) {
                angular.forEach(result, function(v) {
                  v.name = v.name.split('.')[1];
                });

                $scope.responses.schema = result;
              });

            // request preview
            dataSrc
              .request({
                _cdapPath: '/data/explore/queries/' +
                              query.query_handle + '/preview',
                method: 'POST'
              })
              .then(function (result) {
                $scope.responses.results = result;
              });
          }

        };

        $scope.download = function(query) {
          dataSrc
            .request({
              _cdapPath: '/data/explore/queries/' +
                              query.query_handle + '/download',
              method: 'POST'
            })
            .then(function (res) {
              var element = angular.element('<a/>');
              element.attr({
                href: 'data:atachment/csv,' + encodeURIComponent(res),
                target: '_self',
                download: 'result.csv'
              })[0].click();
            });
        };

      }

    };

  });
