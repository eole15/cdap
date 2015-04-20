angular.module(PKG.name + '.commons')
  .directive('myMetricPicker', function (MyDataSource, $stateParams, $log) {

    var dSrc = new MyDataSource();

    function MetricPickerCtrl ($scope) {

      var ns = [$stateParams.namespace,'namespace'].join(' ');

      $scope.available = {
        contexts: [],
        types: ['system', ns],
        names: []
      };

      $scope.metric = {
        context: '',
        type: ns,
        names: [{name: ''}],
        resetNames: function() {
          this.names = [{name: ''}];
        },
        getNames: function() {
          return this.names.map(function(value) {
            return value.name;
          });
        },
        getName: function() {
          return this.getNames().join(', ');
        }
      };

      $scope.addMetricName = function() {
        $scope.metric.names.push({name: ''});
      };

      $scope.deleteMetric = function(idx) {
        if ($scope.metric.names.length == 1) {
          // If its the only metric, simply clear it instead of removing it
          $scope.metric.resetNames();
        } else {
          $scope.metric.names.splice(idx, 1);
        }
      };

    }

    return {
      restrict: 'E',

      require: 'ngModel',

      scope: {},

      templateUrl: 'metric-picker/metric-picker.html',

      controller: MetricPickerCtrl,

      link: function (scope, elem, attr, ngModel) {

        if(attr.required!==undefined) {
          elem.find('input').attr('required', true);
          ngModel.$validators.metricAndContext = function (m, v) {
            var t = m || v;
            if (!t || !t.names || !t.names.length || !t.context) {
              return false;
            }
            for (var i = 0; i < t.names.length; i++) {
              if (!t.names[i].length) {
                return false;
              }
            }
            return true;
          };
        }

        function getBaseContext () {
          var output;

          if(scope.metric.type==='system') {
            output = 'system';
          }
          else {
            output = $stateParams.namespace;

            if(!output) { // should never happen, except on directive playground
              output = 'default';
              $log.warn('metric-picker using default namespace as context!');
            }

          }

          return 'namespace.' + output;
        }

        function fetchAhead () {
          var b = getBaseContext(),
              bLen = b.length+1,
              context = b;

          if(scope.metric.context) {
            context += '.' + scope.metric.context;
          }

          scope.available.contexts = [];
          dSrc.request(
            {
              method: 'POST',
              _cdapPath: '/metrics/search?target=childContext' +
                '&context=' + encodeURIComponent(context)
            },
            function (res) {
              scope.available.contexts = res.map(function(d){
                return {
                  value: d.substring(bLen),
                  display: d.substring(bLen+scope.metric.context.length)
                };
              }).filter(function(d) {
                return d.display;
              });
            }
          );

          scope.available.names = [];
          dSrc.request(
            {
              method: 'POST',
              _cdapPath: '/metrics/search?target=metric' +
                '&context=' + encodeURIComponent(context)
            },
            function (res) {
              // 'Add All' option to add all metrics in current context.
              res.unshift('Add All');
              scope.available.names = res;
            }
          );

        }

        var onBlurHandler = ngModel.$setTouched.bind(ngModel);
        elem.find('button').on('blur', onBlurHandler);
        elem.find('input').on('blur', onBlurHandler);

        var metricChanged = function (newVal, oldVal) {
          ngModel.$validate();

          if(newVal.type !== oldVal.type) {
            scope.metric.context = '';
            scope.metric.resetNames();
            fetchAhead();
            return;
          }

          if(newVal.context !== oldVal.context) {
            scope.metric.resetNames();
            fetchAhead();
            return;
          }

          if(newVal.context && newVal.names) {
            var isAddAll = false;
            for (var i = 0; i < newVal.names.length; i++) {
              if (newVal.names[i].name === 'Add All') {
                isAddAll = true;
              }
            }
            if (isAddAll) {
              ngModel.$setViewValue({
                addAll: true,
                allMetrics: scope.available.names.slice(1), // Remove 'Add All' option
                context: getBaseContext() + '.' + newVal.context,
                names: newVal.getNames(),
                name: newVal.getName()
              });
              return;
            } else {
              ngModel.$setViewValue({
                context: getBaseContext() + '.' + newVal.context,
                names: newVal.getNames(),
                name: newVal.getName()
              });
            }
          } else {
            if(ngModel.$dirty) {
              ngModel.$setViewValue(null);
            }

          }

        }
        scope.$watch('metric', metricChanged, true);

        fetchAhead();
      }
    };
  });
