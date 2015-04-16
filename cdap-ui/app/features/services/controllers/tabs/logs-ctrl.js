angular.module(PKG.name + '.feature.services')
  .controller('ServicesLogsController', function($scope, $state, MyDataSource) {
    var dataSrc = new MyDataSource($scope),
        basePath = '/apps/' + $state.params.appId + '/services/' + $state.params.programId;

    $scope.logs = [];

    dataSrc.poll({
      _cdapNsPath: basePath + '/logs/next?maxSize=50'
    }, function(res) {
      $scope.logs = res;
    });

  });
