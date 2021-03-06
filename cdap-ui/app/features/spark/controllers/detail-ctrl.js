angular.module(PKG.name + '.feature.spark')
  .controller('SparkDetailController', function($scope, MyDataSource, $state, MY_CONFIG) {
    $scope.isEnterprise = MY_CONFIG.isEnterprise;
    var dataSrc = new MyDataSource($scope),
        path = '/apps/' +
          $state.params.appId + '/spark/' +
          $state.params.programId;

    $scope.start = function() {
      $scope.status = 'STARTING';
      dataSrc.request({
        _cdapNsPath: path + '/start',
        method: 'POST'
      });
    };

    $scope.stop = function() {
      $scope.status = 'STOPPING';
      dataSrc.request({
        _cdapNsPath: path + '/stop',
        method: 'POST'
      });
    };

    dataSrc.poll({
      _cdapNsPath: path + '/status'
    }, function(res) {
      $scope.status = res.status;
    });
  });
