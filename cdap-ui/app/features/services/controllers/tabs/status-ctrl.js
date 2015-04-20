angular.module(PKG.name + '.feature.services')
  .controller('ServicesDetailStatusController', function($state, $scope, MyDataSource) {
    var dataSrc = new MyDataSource($scope),
        path = '/apps/' +
          $state.params.appId + '/services/' +
          $state.params.programId;

    $scope.endPoints = [];
    $scope.instances = {};
    $scope.status = '';

    dataSrc.request({
      _cdapNsPath: path
    })
      .then(function(res) {
        angular.forEach(res.handlers, function(value, key) {
          $scope.endPoints = $scope.endPoints.concat(value.endpoints);
        });
        $scope.instances = res.instances;
      });

    dataSrc.request({
      _cdapNsPath: path + '/instances'
    })
      .then(function(res) {
        $scope.instances = res;
      });

    dataSrc.poll({
      _cdapNsPath: path + '/status'
    }, function(res) {
      $scope.status = res.status || 'Unknown';
    });

  });
