angular.module(PKG.name + '.feature.flows')
  .controller('FlowsRunDetailLogController', function($scope, MyDataSource, $state) {

    var dataSrc = new MyDataSource($scope),
        basePath = '/apps/' + $state.params.appId + '/flows/' + $state.params.programId;

    $scope.logs = [];

    dataSrc.poll({
      _cdapNsPath: basePath + '/logs/next?maxSize=50'
    }, function(res) {
      $scope.logs = res;
    });

});
