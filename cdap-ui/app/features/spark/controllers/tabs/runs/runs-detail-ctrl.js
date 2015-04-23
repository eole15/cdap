angular.module(PKG.name + '.feature.spark')
  .controller('SparkRunDetailController', function($scope, $state) {

    $scope.tabs = [{
      title: 'Status',
      template: '/assets/features/spark/templates/tabs/runs/tabs/status.html'
    },
    {
      title: 'Logs',
      template: '/assets/features/spark/templates/tabs/runs/tabs/log.html'
    }];

    $scope.activeTab = $scope.tabs[0];

    $scope.$on('$destroy', function(event) {
      event.targetScope.runs.selected = null;
    });

    $scope.selectTab = function(tab, node) {
      $scope.activeTab = tab;

    };
  });
