angular.module(PKG.name + '.feature.spark')
  .controller('SparkRunsDetailStatusControler', function($state, $scope, MyDataSource, myHelpers, $timeout, $filter) {
    var filterFilter = $filter('filter');
    var dataSrc = new MyDataSource($scope),
        basePath = '/apps/' + $state.params.appId + '/spark/' + $state.params.programId;

    if ($state.params.runid) {
      var match = filterFilter($scope.runs, {runid: $state.params.runid});
      if (match.length) {
        $scope.runs.selected = match[0];
      }
    }
    $scope.data = {
      'blockRemainingMemory': 0,
      'blockMaxMemory': 0,
      'blockUsedMemory': 0,
      'blockDiskSpaceUsed': 0,
      'schedulerActiveJobs': 0,
      'schedulerAllJobs': 0,
      'schedulerFailedStages': 0,
      'schedulerRunningStages': 0,
      'schedulerWaitingStages': 0
    };

    $scope.runningTooltip = {
      "title": 'Running'
    };

    $scope.waitingTooltip = {
      "title": 'Waiting'
    };

    $scope.failedTooltip = {
      "title": 'Failed'
    };

    $scope.$watch('runs.selected.runid', function (newVal, oldVal) {
      if(newVal) {
        pollMetrics(newVal);
      }
    });

    $scope.status = null;
    $scope.duration = null;
    $scope.startTime = null;

    // This controller is NOT shared between the accordions.
    dataSrc.poll({
      _cdapNsPath: basePath + '/runs/' + $scope.runs.selected.runid
    }, function(res) {
      var startMs = res.start * 1000;
        $scope.startTime = new Date(startMs);
        $scope.status = res.status;
        $scope.duration = (res.end ? (res.end * 1000) - startMs : 0);
      });

    $scope.getStagePercentage = function (type) {
      var total = ($scope.data.schedulerRunningStages
        + $scope.data.schedulerFailedStages
        + $scope.data.schedulerWaitingStages);
      switch(type) {
        case 'running':
          return $scope.data.schedulerRunningStages * 100 / total;
        case 'waiting':
          return $scope.data.schedulerWaitingStages * 100 / total;
        case 'failed':
          return $scope.data.schedulerFailedStages * 100 / total;
      }
    }

    function pollMetrics(runId) {
      var metricsBasePath = '/metrics/query?' +
        'tag=namespace:' + $state.params.namespace +
        '&tag=app:' + $state.params.appId +
        '&tag=spark:' + $state.params.programId +
        '&tag=run:' + runId +
        '&metric=' + $state.params.appId;


      var metricPaths = {};
      metricPaths[metricsBasePath + '.BlockManager.memory.remainingMem_MB?aggregate=true'] = 'blockRemainingMemory';
      metricPaths[metricsBasePath + '.BlockManager.memory.maxMem_MB?aggregate=true'] = 'blockMaxMemory';
      metricPaths[metricsBasePath + '.BlockManager.memory.memUsed_MB?aggregate=true'] = 'blockUsedMemory';
      metricPaths[metricsBasePath + '.BlockManager.disk.diskSpaceUsed_MB?aggregate=true'] = 'blockDiskSpaceUsed';
      metricPaths[metricsBasePath + '.DAGScheduler.job.activeJobs?aggregate=true'] = 'schedulerActiveJobs';
      metricPaths[metricsBasePath + '.DAGScheduler.job.allJobs?aggregate=true'] = 'schedulerAllJobs';
      metricPaths[metricsBasePath + '.DAGScheduler.stage.failedStages?aggregate=true'] = 'schedulerFailedStages';
      metricPaths[metricsBasePath + '.DAGScheduler.stage.runningStages?aggregate=true'] = 'schedulerRunningStages';
      metricPaths[metricsBasePath + '.DAGScheduler.stage.waitingStages?aggregate=true'] = 'schedulerWaitingStages';

      angular.forEach(metricPaths, function (path, name) {
        dataSrc.poll({
          _cdapPath: name,
          method: 'POST'
        }, function(res) {
          $scope.data[name] = myHelpers.objectQuery(res, 'series', 0, 'data', 0, 'value') || 0;
        });
      });

    }

  });
