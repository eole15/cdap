var ngC3 = angular.module(PKG.name+'.commons');

var baseDirective = {
  restrict: 'E',
  replace: true,
  template: '<div class="c3"></div>',
  scope: {
    chartData: '='
  },
  controller: 'c3Controller'
};


ngC3.factory('c3', function ($window) {
  return $window.c3;
});

ngC3.controller('c3Controller', function ($scope, caskWindowManager, c3, myHelpers, $filter) {
  // We need to bind because the init function is called directly from the directives below
  // and so the function arguments would not otherwise be available to the initC3 and render functions.
  var caskWindowManager = caskWindowManager;
  var c3 = c3;
  var myHelpers = myHelpers;
  var $filter = $filter;

  $scope.initC3 = function (elem, type, attr, forcedOpts) {
    if($scope.me) {
      return;
    }

    // Default options:
    var options = {stack: false, formatAsTimestamp: true};
    angular.extend(options, forcedOpts || {}, {
      el: elem[0]
    });

    angular.forEach(attr, function (v, k) {
      if ( v && k.indexOf('chart')===0 ) {
        var key = k.substring(5);
        this[key.charAt(0).toLowerCase() + key.slice(1)] = $scope.$eval(v);
      }
    }, options);

    options.data = { x: 'x', columns: [] };

    $scope.type = type;
    $scope.options = options;


    if(attr.chartData) {
      $scope.$watch('chartData', function (chartData) {
        if(chartData) {
          myData = { x: 'x', columns: chartData.columns, keys: {x: 'x'} };

          if ($scope.options.stack) {
            myData.groups = [chartData.metricNames];
          }

          // Save the data for when it gets resized.
          $scope.options.data = myData;

          render()
          // WARN: using load() API has funny animation (when inserting new data points to the right)
//            $scope.me.load(myData);  // Alternative to render()
        }
      });
    }
    render();
  };

  function render() {
    var data = $scope.options.data;
    data.type = $scope.type;

    // Mainly needed for pie chart values to be shown upon tooltip, but also useful for other types.
    var myTooltip = { format: { value: d3.format(',') } };

    var chartConfig = {bindto: $scope.options.el, data: data, tooltip: myTooltip};
    chartConfig.size = $scope.options.size;

    var xTick = {};
    xTick.count = $scope.options.xtickcount
    if ($scope.options.formatAsTimestamp) {
      var timestampFormat = function(timestampSeconds) {
        return $filter('amDateFormat')(timestampSeconds * 1000, 'h:mm:ss a');
      };
      xTick.format = timestampFormat;
    }
    chartConfig.axis = { x: { show: $scope.options.showx,
                              tick : xTick },
                         y: { show: $scope.options.showy } };
    chartConfig.color = $scope.options.color;
    chartConfig.legend = $scope.options.legend;
    chartConfig.point = { show: false };
    if($scope.options.subchart) {
      chartConfig.subchart = $scope.options.subchart;
    }
    chartConfig.zoom = { enabled: true};
    chartConfig.transition = {
                        duration: 1000
                    }
    $scope.me = c3.generate(chartConfig);
  }

  $scope.$on(caskWindowManager.event.resize, render);

});

ngC3.directive('c3Line', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'line', attr, {xtickcount: 5});
    }
  }, baseDirective);
});

ngC3.directive('c3Bar', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'bar', attr, {stack: true});
    }
  }, baseDirective);
});

ngC3.directive('c3Pie', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'pie', attr, {formatAsTimestamp: false});
    }
  }, baseDirective);
});

ngC3.directive('c3Donut', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'donut', attr, {formatAsTimestamp: false});
    }
  }, baseDirective);
});

ngC3.directive('c3Scatter', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'scatter', attr, {xtickcount: 5});
    }
  }, baseDirective);
});

ngC3.directive('c3Spline', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'spline', attr, { xtickcount: 5});
    }
  }, baseDirective);
});

ngC3.directive('c3Step', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'step', attr, {xtickcount: 5});
    }
  }, baseDirective);
});

ngC3.directive('c3Area', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'area', attr, {xtickcount: 5});
    }
  }, baseDirective);
});

ngC3.directive('c3AreaStep', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'area-step', attr, {xtickcount: 5});
    }
  }, baseDirective);
});

ngC3.directive('c3AreaSpline', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'area-spline', attr, {xtickcount: 5} );
    }
  }, baseDirective);
});

ngC3.directive('c3AreaSplineStacked', function () {
  return angular.extend({
    link: function (scope, elem, attr) {
      scope.initC3(elem, 'area-spline', attr, {stack: true, xtickcount: 5});
    }
  }, baseDirective);
});
