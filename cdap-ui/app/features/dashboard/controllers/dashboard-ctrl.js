/**
 * DashboardCtrl
 */

angular.module(PKG.name+'.feature.dashboard').controller('DashboardCtrl',
function ($scope, $state, $dropdown, rDashboardsModel, MY_CONFIG) {

  $scope.unknownBoard = false;
  $scope.isEnterprise = MY_CONFIG.isEnterprise;
  $scope.dashboards = rDashboardsModel.data || [];
  $scope.liveDashboard = null;
  $scope.startMs = Date.now() - 60 * 1000;
  $scope.endMs = Date.now();
  $scope.durationMs = null;
  $scope.dashboards.activeIndex = parseInt($state.params.tab, 10) || 0;

  // Available refresh rates.
  $scope.refreshIntervals = [
    '5 seconds',
    '10 seconds',
    '60 seconds',
    '5 minutes',
  ];
  $scope.refreshIntervalsMap = {
    '5 seconds' : 5 * 1000,
    '10 seconds': 10 * 1000,
    '60 seconds': 60 * 1000,
    '5 minutes' : 300 * 1000,
  }
  $scope.refreshInterval = '5 seconds';

  $scope.currentBoard = rDashboardsModel.current();
  if (!$scope.currentBoard) {
    $scope.unknownBoard = true;
  }

  /**
   * show a dropdown when clicking on the tab of active dashboard
   * @TODO make a directive instead
   */
  $scope.activeTabClick = function (event, index) {
    if (index !== $scope.dashboards.activeIndex || !$state.includes('dashboard.user')) {
      return;
    }
    var toggle = angular.element(event.target);
    if(!toggle.hasClass('dropdown-toggle')) {
      toggle = toggle.parent();
    }

    var scope = $scope.$new(),
        dd = $dropdown(toggle, {
          template: 'assets/features/dashboard/templates/partials/tab-dd.html',
          animation: 'am-flip-x',
          trigger: 'manual',
          prefixEvent: 'dashboard-tab-dd',
          scope: scope
        });

    dd.$promise.then(function(){
      dd.show();
    });

    scope.$on('dashboard-tab-dd.hide', function () {
      dd.destroy();
    });

  };


  $scope.addDashboard = function (title) {
    rDashboardsModel.add({title: title}).then(function() {
      var tabDest = rDashboardsModel.data.length - 1;
      $state.go('dashboard.user', {tab: tabDest}, {reload: true});
    });
  };

  $scope.removeDashboard = function () {
    rDashboardsModel.remove($scope.dashboards.activeIndex)
      .then(function() {
        $state.go('dashboard.standard.cdap', {}, {reload: true});
      });
  };

  $scope.reorderDashboard = function (reverse) {
    var newIndex = rDashboardsModel.reorder(reverse);
    $state.go($state.current, {tab: newIndex}, {reload: true});
  };

  $scope.dragdrop = {
    dragStart: function (drag) {
      console.log('dragStart', drag.source, drag.dest);
    },
    dragEnd: function (drag) {
      console.log('dragEnd', drag.source, drag.dest);
      rDashboardsModel.current().persist();
    }
  };


  function applyOnWidgets(rDashboardsModel, func) {
    var currentColumns = rDashboardsModel.current().columns,
        i, j;
    for (i=0; i<currentColumns.length; i++) {
      for (j=0; j<currentColumns[i].length; j++) {
        func(currentColumns[i][j]);
      }
    }
  }

  // TODO: new widgets added won't have the properties set below

  $scope.updateWithTimeRange = function() {
    // TODO: need to restrict timeRange (too wide a time range causes issues with charting lib - too many points!)
    applyOnWidgets(rDashboardsModel, function (widget) {
      widget.metric.startTime = Math.floor($scope.startMs / 1000);
      widget.metric.endTime = Math.floor($scope.endMs / 1000);
      widget.metric.resolution = 'auto';
      widget.isLive = false;
      widget.reconfigure();
    });
  }

  $scope.updateWithFrequency = function() {
    applyOnWidgets(rDashboardsModel, function (widget) {
      widget.metric.startTime = $scope.durationMs;
      widget.metric.endTime = 'now';
      widget.metric.resolution = 'auto';
      widget.isLive = true;
      widget.interval = $scope.refreshIntervalsMap[$scope.refreshInterval];
      widget.reconfigure();
    });
  }
});
