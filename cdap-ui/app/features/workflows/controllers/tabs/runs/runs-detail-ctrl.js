angular.module(PKG.name + '.feature.flows')
  .controller('WorkflowsRunsDetailController', function($scope) {

    $scope.tabs = [{
      title: 'Status',
      template: '/assets/features/workflows/templates/tabs/runs/tabs/status.html'
    },
    {
      title: 'Logs',
      template: '/assets/features/workflows/templates/tabs/runs/tabs/log.html'
    }];

    $scope.activeTab = $scope.tabs[0];

    $scope.$on('$destroy', function(event) {
      event.targetScope.runs.selected = null;
    });

    $scope.selectTab = function(tab) {
      $scope.activeTab = tab;
    };

  });
