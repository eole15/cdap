/**
 * OverviewCtrl
 */

angular.module(PKG.name+'.feature.overview').controller('OverviewCtrl',
function ($scope, MyDataSource, $state, myLocalStorage, MY_CONFIG, Widget) {

  if(!$state.params.namespace) {
    // the controller for "ns" state should handle the case of
    // an empty namespace. but this nested state controller will
    // still be instantiated. avoid making useless api calls.
    return;
  }

  $scope.apps = [];

  $scope.dataList = [];
  $scope.hideWelcomeMessage = false;


  var dataSrc = new MyDataSource($scope),
      partialPath = '/assets/features/overview/templates/',
      PREFKEY = 'feature.overview.welcomeIsHidden';

  myLocalStorage.get(PREFKEY)
    .then(function (v) {
      $scope.welcomeIsHidden = v;
    });

  $scope.hideWelcome = function () {
    myLocalStorage.set(PREFKEY, true);
    $scope.welcomeIsHidden = true;
  };

  $scope.isEnterprise = MY_CONFIG.isEnterprise;
  $scope.systemStatus = '';
  dataSrc.poll({
    _cdapPath: '/system/services/status',
    interval: 10000
  }, function(res) {
    var serviceStatuses = Object.keys(res).map(function(value, i) {
      return res[value];
    });
    if (serviceStatuses.indexOf('NOT OK') > -1) {
      $scope.systemStatus = 'yellow';
    }
    if (serviceStatuses.indexOf('OK') === -1) {
      $scope.systemStatus = 'red';
    }
    if (serviceStatuses.indexOf('NOT OK') === -1) {
      $scope.systemStatus = 'green';
    }
  });

  dataSrc.request({
    _cdapNsPath: '/apps'
  })
    .then(function(res) {
      $scope.apps = res;
    });

  dataSrc.request({
    _cdapNsPath: '/data/datasets'
  })
    .then(function(res) {
      $scope.dataList = $scope.dataList.concat(res);
    });

  dataSrc.request({
    _cdapNsPath: '/streams'
  }, function(res) {
    if (angular.isArray(res) && res.length) {
      angular.forEach(res, function(r) {
        r.type = 'Stream';
      });

      $scope.dataList = $scope.dataList.concat(res);
    }
  });

  $scope.wdgts = [];
  // type field is overridden by what is rendered in view because we do not use widget.getPartial()
  $scope.wdgts.push(new Widget({title: 'System Health', type: 'c3-line', isLive: true,
                  metric: {
                    context: 'namespace.*',
                    names: ['system.log.error', 'system.log.warn'],
                    startTime: 'now-3600s',
                    endTime: 'now',
                    resolution: '1m'
                  },
                  metricAlias: {'system.log.error': 'System Errors',
                                'system.log.warn' : 'System Warnings'},
                  interval: 60*1000,
                  aggregate: 5
  }));
  $scope.wdgts.push(new Widget({title: 'Applications Health', type: 'c3-line', isLive: true,
                  metric: {
                    context: 'namespace.' + $state.params.namespace,
                    names: ['system.app.log.error', 'system.app.log.warn'],
                    startTime: 'now-3600s',
                    endTime: 'now',
                    resolution: '1m'
                  },
                  metricAlias: {'system.app.log.error': 'Application Errors',
                                'system.app.log.warn' : 'Application Warnings'},
                  interval: 60*1000,
                  aggregate: 5
  }));
});
