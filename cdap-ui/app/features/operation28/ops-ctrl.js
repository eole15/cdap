/**
 * Operation 2.8
 */

angular.module(PKG.name+'.feature.dashboard')
/* ------------------------------------------------------ */

  .controller('Op28CdapCtrl',
  function ($scope, $state, op28helper, MyDataSource) {

    var dataSrc = new MyDataSource($scope);
    // waiting for system.request.received to be changed to query requests
    $scope.panels = [
      ['Collect', 'EPS',   'system.collect.events'],
      ['Process', 'events',     'system.process.events.processed'],
      ['Store',   '/S',    'system.dataset.store.bytes', true],
      ['Query',   'QPS',   'system.request.received']
    ].map(op28helper.panelMap);

    angular.forEach($scope.panels, function (panel) {
      var c = panel.chart;
      console.log('c', c.metric);
      dataSrc.poll({
          _cdapPath: '/metrics/query?metric=' +
              c.metric + '&start=now-60s&end=now',
          interval: 1000,
          method: 'POST'
        },
        op28helper.pollCb.bind(c)
      );
    });

  })


/* ------------------------------------------------------ */


  .controller('Op28SystemCtrl',
  function ($scope, op28helper, MyDataSource) {

    var dataSrc = new MyDataSource($scope);

    $scope.panels = [
      ['AppFabric',  'Containers', 'system.resources.used.containers'],
      ['Processors', 'Cores',      'system.resources.used.vcores'],
      ['Memory',     '',           'system.resources.used.memory', true],
      ['DataFabric', '',           'system.resources.used.storage', true]
    ].map(op28helper.panelMap);

    angular.forEach($scope.panels, function (panel) {
      var c = panel.chart;
      dataSrc.poll({
          _cdapPath: '/metrics/metric?.' +
                        c.metric +
                        '&start=now-60s&end=now',
          interval: 60 * 1000,
          method: 'POST'
        },
        op28helper.pollCb.bind(c)
      );
    });

  })


/* ------------------------------------------------------ */


  .controller('Op28AppsCtrl',
  function ($scope, $state, myHelpers, MyDataSource) {

    var dataSrc = new MyDataSource($scope);

    $scope.apps = [];

    dataSrc
      .request({
        _cdapNsPath: '/apps'
      },
      function (apps) {
        $scope.apps = apps;

        var m = ['vcores', 'containers', 'memory'];

        for (var i = 0; i < m.length; i++) {

          dataSrc
            .poll({
              _cdapPath: '/metrics/query' +
                '?context=namespace.system' +
                '&metric=system.resources.used.' +
                m[i] + '&groupBy=app',
              method: 'POST'
            }, setMetric);
        }

      });

    function setMetric(r) {

      angular.forEach($scope.apps, function (app) {
        angular.forEach(r.series, function (s) {
          if(app.id === s.grouping.app) {
            myHelpers.deepSet(
              app,
              'metric.' + s.metricName.split('.').pop(),
              s.data[0].value
            );
          }
        });
      });

    }

  })


/* ------------------------------------------------------ */


  .factory('op28helper', function (myHelpers) {

    function panelMap (d) {
      return {
        title: d[0],
        unit: d[1],
        useByteFilter: d[3],
        chart: {
          metric: d[2],
          context: 'system',
          history: null,
          stream: null,
          lastValue: 0
        }
      };
    }


    function pollCb (res) {
      var result = myHelpers.objectQuery(res, 'series', 0, 'data');
      if (!result) {
        return;
      }
      var v = result.map(function (o) {
        return {
          time: o.time,
          y: o.value
        };
      });
      if(this.history) {
        this.stream = v.slice(-1); // because poll is once per second
      }
      this.history = [{
        label: this.metric,
        values: v
      }];
      this.lastValue = v.pop().value;
    }

    return {
      panelMap: panelMap,
      pollCb: pollCb
    };

  })

  ;
