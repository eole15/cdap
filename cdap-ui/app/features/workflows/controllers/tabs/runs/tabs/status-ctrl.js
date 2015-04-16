angular.module(PKG.name + '.feature.workflows')
  .controller('WorkflowsRunsStatusController', function($state, $scope, MyDataSource, $filter, $state) {
    var dataSrc = new MyDataSource($scope),
        filterFilter = $filter('filter'),
        basePath = '/apps/' + $state.params.appId + '/workflows/' + $state.params.programId;

    if ($state.params.runid) {
      var match = filterFilter($scope.runs, {runid: $state.params.runid});
      if (match.length) {
        $scope.runs.selected = match[0];
      }
    }
    $scope.status = null;
    $scope.duration = null;
    $scope.startTime = null;
    $scope.data = {};
    dataSrc.request({
      _cdapNsPath: basePath
    })
      .then(function(res) {
        var edges = [],
            nodes = [];

        convert(angular.copy(res.nodes), edges);
        expandForks(res.nodes, nodes);

        nodes = nodes.map(function(item) {
          return angular.extend({
            name: item.program.programName + item.nodeId,
            type: item.nodeType
          }, item);
        });

        addStartAndEndNodes(nodes, edges);

        $scope.data = {
          nodes: nodes,
          edges: edges,
          metrics: {}
        };

        var programs = [];
        angular.forEach(res.nodes, function(value, key) {
          programs.push(value.program);
        });
        $scope.actions = programs;
      });


    $scope.goToDetailActionView = function(programId, programType) {
      // As of 2.7 only a mapreduce job is scheduled in a workflow.
      if (programType === 'MAPREDUCE') {
        $state.go('mapreduce.detail', {
          programId: programId
        });
      }
    };


    dataSrc.poll({
      _cdapNsPath: basePath + '/runs/' + $scope.runs.selected.runid
    }, function(res) {
        startMs = res.start * 1000;
        $scope.startTime = new Date(startMs);
        $scope.status = res.status;
        $scope.duration = (res.end ? (res.end * 1000) - startMs : 0);
    });
  });

/**
 * Adds start and end nodes to nodes list.
 * @param {Array} of nodes.
 */
function addStartAndEndNodes(nodes, edges) {
  if (nodes.length) {
    nodes.unshift({
      name: 'start',
      type: 'START',
      nodeType: 'START'
    });
    edges.unshift({
      sourceName: nodes[0].name,
      sourceType: nodes[0].nodeType,
      targetName: nodes[1].name
    });

    nodes.push({
      name: 'end',
      type: 'END',
      nodeType: 'END'
    });
    edges.push({
      sourceName: nodes[nodes.length - 2].name,
      sourceType: nodes[nodes.length - 2].nodeType,
      targetName: nodes[nodes.length - 1].name
    });

  }

}

/**
  * Purpose: Converts a list of nodes to a list of connections
  * @param  [Array] of nodes
  * @return [Array] of connections
  * Usage: Can handle all cases, including:
      1. Fork in the middle
      2. Only a fork
      3. Fork at the beginning
      4. Fork at the end
      5. Only an Action node

      var z = [
        {
          nodeType: 'ACTION',
          program: {
            programName: "asd"
          }
        }, {
          nodeType: 'FORK',
          branches: [
            [
              [
                {
                  nodeType: 'ACTION',
                  program: {
                    programName: "1"
                  }
                }
              ],
              [
                {
                  nodeType: 'ACTION',
                  program: {
                    programName: "2"
                  }
                }
              ]
            ],
            [
              {
                nodeType: 'ACTION',
                program: {
                  programName: "3"
                }
              }
            ]
          ]
        }, {
          nodeType: 'ACTION',
          program: {
            programName: "4"
          }
        }
      ];
*/
function convert(nodes, connections) {

  for (var i=0; i+1 < nodes.length; i++) {

    if ( i === 0 && nodes[i].nodeType === 'FORK') {
      flatten(null, nodes[i], nodes[i+1], connections);
    }

    if (nodes[i].nodeType === 'ACTION' && nodes[i+1].nodeType === 'ACTION') {
      connections.push({
        sourceName: nodes[i].program.programName + nodes[i].nodeId,
        targetName: nodes[i+1].program.programName + nodes[i+1].nodeId,
        sourceType: nodes[i].nodeType
      });
    } else if (nodes[i].nodeType === 'FORK') {
      flatten(nodes[i-1], nodes[i], nodes[i+1], connections);
    }

    if ( (i+1 === nodes.length-1) && nodes[i+1].nodeType === 'FORK') {
      flatten(nodes[i], nodes[i+1], null, connections);
    }
  }
}

/**
  * Purpose: Flatten a source-fork-target combo to a list of connections
  * @param  [Array] of nodes
  * @param  [Array] of nodes
  * @param  [Array] of nodes
  * @return [Array] of connections

*/
function flatten(source, fork, target, connections) {
  var branches = fork.branches,
      temp = [];

  for (var i =0; i<branches.length; i++) {
    temp = branches[i];
    if(source) {
      temp.unshift(source);
    }
    if(target) {
      temp.push(target);
    }
    convert(temp, connections);
  }
}

/**
  Purpose: Expand a fork and convert branched nodes to a list of connections
  * @param  [Array] of nodes
  * @return [Array] of connections

*/
function expandForks(nodes, expandedNodes) {
  for(var i=0; i<nodes.length; i++) {
    if (nodes[i].nodeType === 'ACTION') {
      expandedNodes.push(nodes[i]);
    } else if (nodes[i].nodeType === 'FORK') {
      for (var j=0; j<nodes[i].branches.length; j++) {
        expandForks(nodes[i].branches[j], expandedNodes);
      }
    }
  }
}
