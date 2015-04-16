angular.module(PKG.name + '.feature.mapreduce')
  .config(function($stateProvider, $urlRouterProvider, MYAUTH_ROLE) {
    $stateProvider
      .state('mapreduce', {
        url: '/mapreduce',
        abstract: true,
        parent: 'programs',
        data: {
          authorizedRoles: MYAUTH_ROLE.all,
          highlightTab: 'development'
        },
        template: '<ui-view/>'
      })

      .state('mapreduce.detail', {
        url: '/:programId',
        data: {
          authorizedRoles: MYAUTH_ROLE.all,
          highlightTab: 'development'
        },
        templateUrl: '/assets/features/mapreduce/templates/detail.html',
        controller: 'MapreduceDetailController',
        ncyBreadcrumb: {
          parent: 'apps.detail.overview',
          label: '{{$state.params.programId}}'
        }
      })
        .state('mapreduce.detail.status', {
          url: '/status',
          templateUrl: '/assets/features/mapreduce/templates/tabs/status.html',
          controller: 'MapreduceStatusController',
          data: {
            authorizedRoles: MYAUTH_ROLE.all,
            highlightTab: 'development'
          },
          ncyBreadcrumb: {
            parent: 'apps.detail.overview',
            label: 'Mapreduce',
            skip: true
          }
        })

        .state('mapreduce.detail.runs', {
          url: '/runs',
          templateUrl: '/assets/features/mapreduce/templates/tabs/runs.html',
          controller: 'MapreduceRunsController',
          ncyBreadcrumb: {
            skip: true
          }
        })
          .state('mapreduce.detail.runs.run', {
            url: '/:runid',
            templateUrl: '/assets/features/mapreduce/templates/tabs/runs/run-detail.html',
            controller: 'MapreduceRunsDetailController',
            ncyBreadcrumb: {
              skip: true
            }
          })
        .state('mapreduce.detail.history', {
          url: '/history',
          templateUrl: '/assets/features/mapreduce/templates/tabs/history.html',
          ncyBreadcrumb: {
            parent: 'apps.detail.overview',
            label: '{{$state.params.programId}} < History'
          }
        });
  });
