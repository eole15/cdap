angular.module(PKG.name + '.feature.etlapps')
  .controller('ETLAppsCreateController', function($scope, $q, $alert, $state, ETLAppsApiFactory, mySettings, $filter, $rootScope) {
    var apiFactory = new ETLAppsApiFactory($scope);
    $scope.ETLMetadataTabOpen = true;
    $scope.ETLSourcesTabOpen = true;
    $scope.ETLTransformsTabOpen = true;
    $scope.ETLSinksTabOpen = true;

    // Loading flag to indicate source & sinks have
    // not been loaded yet (after/before choosing an etl template)
    $scope.loadingEtlSourceProps = false;
    $scope.loadingEtlSinkProps = false;
    $scope.onETLTypeSelected = false;

    // List of ETL Sources, Sinks & Transforms
    // for a particular etl template type fetched from backend.
    $scope.etlSources = [];
    $scope.etlSinks = [];
    $scope.etlTransforms = [];
    $scope.selectedEtlDraft = undefined;
    $scope.etlDraftList = [];

    $scope.onDraftChange = function(item, model) {
      var filterFilter = $filter('filter'),
          match = null,
          swapObj = {};
      if (!item) {
        return; //un-necessary.
      }
      if ($scope.etlDrafts[item]) {
        $scope.metadata = $scope.etlDrafts[item].config.metadata;
        $scope.source = $scope.etlDrafts[item].config.source;
        $scope.sink = $scope.etlDrafts[item].config.sink;
        $scope.transforms = $scope.etlDrafts[item].config.transforms;
      } else {
        $scope.metadata.name = item;
        $scope.metadata.type = $scope.metadata.type;
        $scope.transforms = defaultTransforms;
        $scope.source = defaultSource;
        $scope.sink = defaultSink;
      }
    };

    // Default ETL Templates
    $scope.etlTypes = [
      {
        name: 'Etl Batch',
        type: 'etlbatch'
      },
      {
        name: 'ETL Real Time',
        type: 'realtime'
      }
    ];

    // Metadata Model
    $scope.metadata = {
        name: '',
        description: '',
        type: 'etlbatch'
    };

    var defaultSource = {
      name: 'Add a Source',
      properties: {},
      placeHolderSource: true
    };

    var defaultSink = {
      name: 'Add a Sink',
      placeHolderSink: true,
      properties: {}
    };

    var defaultTransforms = [{
      name: 'Add a Transforms',
      placeHolderTransform: true,
      properties: {}
    },
    {
      name: 'Add a Transforms',
      placeHolderTransform: true,
      properties: {}
    },
    {
      name: 'Add a Transforms',
      placeHolderTransform: true,
      properties: {}
    }];

    // Source, Sink and Transform Models
    $scope.source = defaultSource;
    $scope.sink = defaultSink;
    $scope.transforms = defaultTransforms;
    $scope.activePanel = 0;

    $scope.$watch('metadata.type',function(etlType) {
      if (!etlType) return;
      $scope.onETLTypeSelected = true;
      apiFactory.fetchSources(etlType);
      apiFactory.fetchSinks(etlType);
      apiFactory.fetchTransforms(etlType);
    });

    $scope.handleSourceDrop = function(sourceName) {
      if ($scope.source.placeHolderSource) {
        delete $scope.source.placeHolderSource;
      }
      $scope.source.name = sourceName;
      apiFactory.fetchSourceProperties(sourceName);
    };
    $scope.handleTransformDrop = function(transformName) {
      var i,
          filterFilter = $filter('filter'),
          isPlaceHolderExist;
      isPlaceHolderExist = filterFilter($scope.transforms, {placeHolderTransform: true});
      if (isPlaceHolderExist.length) {
        for (i=0; i<$scope.transforms.length; i+=1) {
          if ($scope.transforms[i].placeHolderTransform) {
            $scope.transforms[i].name = transformName;
            delete $scope.transforms[i].placeHolderTransform;
            apiFactory.fetchTransformProperties(transformName, i);
            break;
          }
        }
        if (i === $scope.transforms.length) {
          $scope.transforms.push({
            name: transformName
          });
          apiFactory.fetchTransformProperties(transformName);
        }
      } else {
        $scope.transforms.push({
          name: transformName,
          properties: apiFactory.fetchTransformProperties(transformName)
        });
      }
    };
    $scope.handleSinkDrop = function(sinkName) {
      if ($scope.sink.placeHolderSink) {
        delete $scope.sink.placeHolderSink;
      }
      $scope.sink.name = sinkName;
      apiFactory.fetchSinkProperties(sinkName);
    };

    $scope.editSourceProperties = function() {
      if ($scope.source.placeHolderSource) {
        return;
      }
      var filterFilter = $filter('filter'),
          match;
      match = filterFilter($scope.tabs, {type: 'source'});
      if (match.length) {
        $scope.tabs[$scope.tabs.indexOf(match[0])].active = true;
      } else {
        $scope.tabs.push({
          title: $scope.source.name,
          type: 'source',
          active: true,
          partial: '/assets/features/etlapps/templates/create/tabs/sourcePropertyEdit.html'
        })
      }
    };
    $scope.editSinkProperties = function() {
      if ($scope.sink.placeHolderSink) {
        return;
      }

      var filterFilter = $filter('filter'),
          match;
      match = filterFilter($scope.tabs, {type: 'sink'});
      if (match.length) {
        $scope.tabs[$scope.tabs.indexOf(match[0])].active = true;
      } else {
        $scope.tabs.active = ($scope.tabs.push({
          title: $scope.sink.name,
          type: 'sink',
          active: true,
          partial: '/assets/features/etlapps/templates/create/tabs/sinkPropertyEdit.html'
        })) -1;
      }
    };
    $scope.editTransformProperty = function(transform) {
      if (transform.placeHolderTransform){
        return;
      }
      var filterFilter = $filter('filter'),
          match;
      match = filterFilter($scope.tabs, {
        transformid: transform.$$hashKey,
        type: 'transform'
      });
      if (match.length) {
        $scope.tabs[$scope.tabs.indexOf(match[0])].active = true;
      } else {
        $scope.tabs.active = ($scope.tabs.push({
          title: transform.name,
          transformid: transform.$$hashKey,
          transform: transform,
          active: true,
          type: 'transform',
          partial: '/assets/features/etlapps/templates/create/tabs/transformPropertyEdit.html'
        })) -1;
      }
    };

    $scope.deleteTransformProperty = function(transform) {
      var index = $scope.transforms.indexOf(transform);
      $scope.transforms.splice(index, 1);
      if (!$scope.transforms.length) {
        $scope.transforms.push({
          name: 'Add a Transforms',
          placeHolderTransform: true,
          properties: {}
        });
      }
    };

    $scope.doSave = function() {
      var transforms = [],
          i;

      if ($scope.source.placeHolderSource || $scope.sink.placeHolderSource) {
        return;
      }
      for(i=0; i<$scope.transforms.length; i+=1) {
        if (!$scope.transforms[i].placeHolderTransform) {
          transforms.push($scope.transforms[i]);
        }
      }
      var data = {
        template: $scope.metadata.type,
        config: {
          source: $scope.source,
          sink: $scope.sink,
          transforms: transforms
        }
      };
      apiFactory.save(data);
    }

    $scope.dragdrop = {
      dragStart: function (drag) {
        console.log('dragStart', drag.source, drag.dest);
      },
      dragEnd: function (drag) {
        console.log('dragEnd', drag.source, drag.dest);
      }
    };

    $scope.getDrafts = function() {
      var defer = $q.defer();
      return mySettings.get('etldrafts')
        .then(function(res) {
          $scope.etlDrafts = res || {};
          $scope.etlDraftList = Object.keys($scope.etlDrafts);
          defer.resolve();
        });
      return defer.promise;
    };
    if ($state.params.data) {
      $scope.getDrafts()
        .then(function() {
          $scope.selectedEtlDraft = $state.params.data;
          $scope.onDraftChange($state.params.data);
        });
    };
    $scope.getDrafts();

    $scope.saveAsDraft = function() {
      if (!$scope.metadata.name.length) {
        $alert({
          type: info,
          content: 'Please provide a name for the Adapter to be saved as draft'
        });
        return;
      }
      $scope.etlDrafts[$scope.metadata.name] = {
        config: {
          metadata: $scope.metadata,
          source: $scope.source,
          transforms: $scope.transforms,
          sink: $scope.sink
        }
      };

      mySettings.set('etldrafts', $scope.etlDrafts)
      .then(function(res) {
        $alert({
          type: 'success',
          content: 'The ETL Template ' + $scope.metadata.name + ' has been saved as draft!'
        });
        $state.go('^.list');
      });
    }

    $scope.tabs = [
      {
        title: 'Default',
        isCloseable: false,
        partial: '/assets/features/etlapps/templates/create/tabs/default.html'
      }
    ];

    $scope.closeTab = function(index) {
      $scope.tabs.splice(index, 1);
    }

    $scope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (fromState.name === 'adapters.create') {
        if(!confirm("Are you sure you want to leave this page?")) {
          event.preventDefault();
        }
      }
    });
  });
