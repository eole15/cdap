/**
 * Widget model & controllers
 */

angular.module(PKG.name+'.feature.dashboard')
  .factory('Widget', function (MyDataSource) {

    function Widget (opts) {
      opts = opts || {};
      this.title = opts.title || 'Widget';
      this.type = opts.type;
      this.metric = opts.metric || false;
      this.color = opts.color;
      this.dataSrc = null;
      this.isLive = false;
    }

    // 'ns.default.app.foo' -> {'ns': 'default', 'app': 'foo'}
    function contextToTags(context) {
      parts = context.split('.')
      if (parts.length % 2 != 0) {
        throw "Metrics context has uneven number of parts: " + this.metric.context
      }
      tags = {}
      for (var i = 0; i < parts.length; i+=2) {
        tags[parts[i]] = parts[i + 1]
      }
      return tags
    }

    function constructQuery(queryId, tags, metrics) {
      timeRange = {'start': 'now-60s', 'end': 'now'}
      queryObj = {tags: tags, metrics: metrics, groupBy: [], timeRange: timeRange}
      retObj = {}
      retObj[queryId] = queryObj
      return retObj
    }

    Widget.prototype.fetchData = function (scope) {
      var dataSrc = new MyDataSource(scope);
      if (!this.metric) {
        return;
      }
      queryId = "qid"
      tags = contextToTags(this.metric.context)
      if (this.metric.name === 'latency.histo') {
        metrics = [
        "user.lat.0-10ms",
        "user.lat.11-100ms",
        "user.lat.101-500ms",
        "user.lat.501-1000ms",
        "user.lat.1001-2000ms",
        "user.lat.2001-3000ms",
        "user.lat.3001-4000ms",
        "user.lat.4001-5000ms",
        "user.lat.5001-10000ms",
        "user.lat.>10000ms" ];
      } else {
        metrics = [this.metric.name];
      }
      dataSrc.poll(
        {
          _cdapPath: '/metrics/query',
          method: 'POST',
          body: constructQuery(queryId, tags, metrics),
        },
        (function (result) {
          var data, tempMap = {};
          result = result[queryId]
          for (var i = 0; i < metrics.length; i++) {
            var metric = metrics[i];
            tempMap[metric] = {};
            // interpolating the data since backend returns only metrics at specific time periods instead of
            // for the whole range. We have to interpolate the rest with 0s to draw the graph.
            for (var j = result.startTime; j < result.endTime; j++) {
              tempMap[metric][j] = 0;
            }
          }
          for (var i = 0; i < result.series.length; i++) {
            data = result.series[i].data;
            metric = result.series[i].metricName
            for (var k = 0 ; k < data.length; k++) {
              tempMap[metric][data[k].time] = data[k].value;
            }
          }
          tmpdata = [];
          for (var i = 0; i < metrics.length; i++) {
            tmpdata.push(tempMap[metrics[i]]);
          }
          this.data = tmpdata;
        }).bind(this)
      );
    };

    Widget.prototype.stopPolling = function(id) {
      if (!this.dataSrc) return;
      this.dataSrc.stopPoll(id);
    };

    Widget.prototype.processData = function (result) {
      var data, tempMap = {};
      if(result.series && result.series.length) {
        data = result.series[0].data;
        for (var k =0 ; k<data.length; k++) {
          tempMap[data[k].time] = data[k].value;
        }
      }
      // interpolating the data since backend returns only
      // metrics at specific timeperiods instead of for the
      // whole range. We have to interpolate the rest with 0s to draw the graph.
      for(var i = result.startTime; i<result.endTime; i++) {
        if (!tempMap[i]) {
          tempMap[i] = 0;
        }
      }
      this.data = tempMap;
    };

    Widget.prototype.getPartial = function () {
      return '/assets/features/dashboard/templates/widgets/' + this.type + '.html';
    };

    Widget.prototype.getClassName = function () {
      return 'panel-default widget widget-' + this.type;
    };

    return Widget;

  })

  .controller('WidgetColCtrl', function ($scope) {
    $scope.colWidth = {
      fullWidth: false,
      oneThird: true
    };
  })

  .controller('WidgetTimeseriesCtrl', function ($scope) {
    var pollingId = null;
    $scope.$watch('wdgt.isLive', function(newVal) {
      if (!angular.isDefined(newVal)) {
        return;
      }
      if (newVal) {
        pollingId = $scope.wdgt.startPolling();
      } else {
        $scope.wdgt.stopPolling(pollingId);
      }
    });
    $scope.wdgt.fetchData($scope);
    $scope.chartHistory = null;
    $scope.stream = null;
    $scope.$watch('wdgt.data', function (newVal) {
      if(angular.isObject(newVal)) {
        var vs = [];
        for (var i = 0; i < newVal.length; i++) {
          vs.push(Object.keys(newVal[i]).map(function(key) {
            return {
              time: key,
              y: newVal[i][key]
            };
          }));
        }

        if ($scope.chartHistory) {
          arr = [];
          for (var i = 0; i < vs.length; i++) {
            var el = vs[i];
            var lastIndex = el.length-1;
            arr.push(el[lastIndex])
          }
          $scope.stream = [arr]
        }

        hist = [];
        for (var i = 0; i < vs.length; i++) {
          hist.push({label: $scope.wdgt.metric.name, values: vs[i]});
        }
        $scope.chartHistory = hist;

      }
    });

  })

  .controller('WidgetPieCtrl', function ($scope, $alert, MyDataSource) {

    $alert({
      content: 'pie chart using fake data',
      type: 'warning'
    });

    $scope.pieChartData = [
      { label: 'Slice 1', value: 10 },
      { label: 'Slice 2', value: 20 },
      { label: 'Slice 3', value: 40 },
      { label: 'Slice 4', value: 30 }
    ];

  });
