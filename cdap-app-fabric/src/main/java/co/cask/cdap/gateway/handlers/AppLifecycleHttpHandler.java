/*
 * Copyright © 2014-2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.gateway.handlers;

import co.cask.cdap.api.ProgramSpecification;
import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.flow.FlowSpecification;
import co.cask.cdap.api.flow.FlowletConnection;
import co.cask.cdap.api.metrics.MetricDeleteQuery;
import co.cask.cdap.api.metrics.MetricStore;
import co.cask.cdap.api.schedule.SchedulableProgramType;
import co.cask.cdap.api.workflow.WorkflowSpecification;
import co.cask.cdap.app.ApplicationSpecification;
import co.cask.cdap.app.deploy.Manager;
import co.cask.cdap.app.deploy.ManagerFactory;
import co.cask.cdap.app.program.Programs;
import co.cask.cdap.app.runtime.ProgramController;
import co.cask.cdap.app.runtime.ProgramRuntimeService;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.http.AbstractBodyConsumer;
import co.cask.cdap.common.namespace.NamespacedLocationFactory;
import co.cask.cdap.common.utils.DirUtils;
import co.cask.cdap.config.PreferencesStore;
import co.cask.cdap.data.dataset.DatasetCreationSpec;
import co.cask.cdap.data2.registry.UsageRegistry;
import co.cask.cdap.data2.transaction.queue.QueueAdmin;
import co.cask.cdap.data2.transaction.stream.StreamConsumerFactory;
import co.cask.cdap.gateway.auth.Authenticator;
import co.cask.cdap.gateway.handlers.util.AbstractAppFabricHttpHandler;
import co.cask.cdap.internal.UserErrors;
import co.cask.cdap.internal.UserMessages;
import co.cask.cdap.internal.app.deploy.ProgramTerminator;
import co.cask.cdap.internal.app.deploy.pipeline.ApplicationWithPrograms;
import co.cask.cdap.internal.app.deploy.pipeline.DeploymentInfo;
import co.cask.cdap.internal.app.namespace.NamespaceAdmin;
import co.cask.cdap.internal.app.runtime.adapter.AdapterService;
import co.cask.cdap.internal.app.runtime.flow.FlowUtils;
import co.cask.cdap.internal.app.runtime.schedule.Scheduler;
import co.cask.cdap.internal.app.runtime.schedule.SchedulerException;
import co.cask.cdap.proto.ApplicationDetail;
import co.cask.cdap.proto.DatasetDetail;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramRecord;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.ProgramTypes;
import co.cask.cdap.proto.StreamDetail;
import co.cask.http.BodyConsumer;
import co.cask.http.HttpResponder;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.twill.filesystem.Location;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * {@link co.cask.http.HttpHandler} for managing application lifecycle.
 */
@Singleton
@Path(Constants.Gateway.API_VERSION_3 + "/namespaces/{namespace-id}")
public class AppLifecycleHttpHandler extends AbstractAppFabricHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(AppLifecycleHttpHandler.class);

  /**
   * Runtime program service for running and managing programs.
   */
  private final ProgramRuntimeService runtimeService;

  /**
   * Store manages non-runtime lifecycle.
   */
  private final Store store;

  private final CConfiguration configuration;
  private final ManagerFactory<DeploymentInfo, ApplicationWithPrograms> managerFactory;
  private final Scheduler scheduler;
  private final StreamConsumerFactory streamConsumerFactory;
  private final QueueAdmin queueAdmin;
  private final PreferencesStore preferencesStore;
  private final AdapterService adapterService;
  private final NamespaceAdmin namespaceAdmin;
  private final MetricStore metricStore;
  private final NamespacedLocationFactory namespacedLocationFactory;
  private final UsageRegistry usageRegistry;

  @Inject
  public AppLifecycleHttpHandler(Authenticator authenticator, CConfiguration configuration,
                                 ManagerFactory<DeploymentInfo, ApplicationWithPrograms> managerFactory,
                                 Scheduler scheduler, ProgramRuntimeService runtimeService, Store store,
                                 StreamConsumerFactory streamConsumerFactory, QueueAdmin queueAdmin,
                                 PreferencesStore preferencesStore, AdapterService adapterService,
                                 NamespaceAdmin namespaceAdmin, MetricStore metricStore,
                                 NamespacedLocationFactory namespacedLocationFactory,
                                 UsageRegistry usageRegistry) {
    super(authenticator);
    this.configuration = configuration;
    this.managerFactory = managerFactory;
    this.namespaceAdmin = namespaceAdmin;
    this.scheduler = scheduler;
    this.runtimeService = runtimeService;
    this.namespacedLocationFactory = namespacedLocationFactory;
    this.store = store;
    this.streamConsumerFactory = streamConsumerFactory;
    this.queueAdmin = queueAdmin;
    this.preferencesStore = preferencesStore;
    this.adapterService = adapterService;
    this.metricStore = metricStore;
    this.usageRegistry = usageRegistry;
  }

  /**
   * Deploys an application with the specified name.
   */
  @PUT
  @Path("/apps/{app-id}")
  public BodyConsumer deploy(HttpRequest request, HttpResponder responder,
                             @PathParam("namespace-id") final String namespaceId,
                             @PathParam("app-id") final String appId,
                             @HeaderParam(ARCHIVE_NAME_HEADER) final String archiveName) {
    try {
      return deployApplication(responder, namespaceId, appId, archiveName);
    } catch (Exception ex) {
      responder.sendString(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Deploy failed: {}" + ex.getMessage());
      return null;
    }

  }

  /**
   * Deploys an application.
   */
  @POST
  @Path("/apps")
  public BodyConsumer deploy(HttpRequest request, HttpResponder responder,
                             @PathParam("namespace-id") final String namespaceId,
                             @HeaderParam(ARCHIVE_NAME_HEADER) final String archiveName) {
    // null means use name provided by app spec
    try {
      return deployApplication(responder, namespaceId, null, archiveName);
    } catch (Exception ex) {
      responder.sendString(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Deploy failed: " + ex.getMessage());
      return null;
    }
  }

  /**
   * Returns a list of applications associated with a namespace.
   */
  @GET
  @Path("/apps")
  public void getAllApps(HttpRequest request, HttpResponder responder,
                         @PathParam("namespace-id") String namespaceId) {
    getAppRecords(responder, store, namespaceId, null);
  }

  /**
   * Returns the info associated with the application.
   */
  @GET
  @Path("/apps/{app-id}")
  public void getAppInfo(HttpRequest request, HttpResponder responder,
                         @PathParam("namespace-id") String namespaceId,
                         @PathParam("app-id") final String appId) {
    getAppDetails(responder, namespaceId, appId);
  }

  /**
   * Delete an application specified by appId.
   */
  @DELETE
  @Path("/apps/{app-id}")
  public void deleteApp(HttpRequest request, HttpResponder responder,
                        @PathParam("namespace-id") String namespaceId,
                        @PathParam("app-id") final String appId) throws Exception {

    Id.Application id = Id.Application.from(namespaceId, appId);
    AppFabricServiceStatus appStatus = removeApplication(id);
    LOG.trace("Delete call for Application {} at AppFabricHttpHandler", appId);
    responder.sendString(appStatus.getCode(), appStatus.getMessage());
  }

  /**
   * Deletes all applications in CDAP.
   */
  @DELETE
  @Path("/apps")
  public void deleteAllApps(HttpRequest request, HttpResponder responder,
                            @PathParam("namespace-id") String namespaceId) throws Exception {

    Id.Namespace id = Id.Namespace.from(namespaceId);
    AppFabricServiceStatus status = removeAll(id);
    LOG.trace("Delete all call at AppFabricHttpHandler");
    responder.sendString(status.getCode(), status.getMessage());
  }

  private BodyConsumer deployApplication(final HttpResponder responder,
                                         final String namespaceId, final String appId,
                                         final String archiveName) throws IOException {
    Id.Namespace namespace = Id.Namespace.from(namespaceId);
    if (!namespaceAdmin.hasNamespace(namespace)) {
      LOG.warn("Namespace '{}' not found.", namespaceId);
      responder.sendString(HttpResponseStatus.NOT_FOUND,
                           String.format("Deploy failed - namespace '%s' not found.", namespaceId));
      return null;
    }

    Location namespaceHomeLocation = namespacedLocationFactory.get(namespace);
    if (!namespaceHomeLocation.exists()) {
      String msg = String.format("Home directory %s for namespace %s not found",
                                 namespaceHomeLocation.toURI().getPath(), namespaceId);
      LOG.error(msg);
      responder.sendString(HttpResponseStatus.NOT_FOUND, msg);
      return null;
    }


    if (archiveName == null || archiveName.isEmpty()) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, ARCHIVE_NAME_HEADER + " header not present",
                           ImmutableMultimap.of(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE));
      return null;
    }

    // Store uploaded content to a local temp file
    String namespacesDir = configuration.get(Constants.Namespace.NAMESPACES_DIR);
    File localDataDir = new File(configuration.get(Constants.CFG_LOCAL_DATA_DIR));
    File namespaceBase = new File(localDataDir, namespacesDir);
    File tempDir = new File(new File(namespaceBase, namespaceId),
                            configuration.get(Constants.AppFabric.TEMP_DIR)).getAbsoluteFile();
    if (!DirUtils.mkdirs(tempDir)) {
      throw new IOException("Could not create temporary directory at: " + tempDir);
    }

    String appFabricDir = configuration.get(Constants.AppFabric.OUTPUT_DIR);
    // note: cannot create an appId subdirectory under the namespace directory here because appId could be null here
    final Location archive =
      namespaceHomeLocation.append(appFabricDir).append(Constants.ARCHIVE_DIR).append(archiveName);

    return new AbstractBodyConsumer(File.createTempFile("app-", ".jar", tempDir)) {

      @Override
      protected void onFinish(HttpResponder responder, File uploadedFile) {
        try {
          DeploymentInfo deploymentInfo = new DeploymentInfo(uploadedFile, archive);
          deploy(namespaceId, appId, deploymentInfo);
          responder.sendString(HttpResponseStatus.OK, "Deploy Complete");
        } catch (Exception e) {
          LOG.error("Deploy failure", e);
          responder.sendString(HttpResponseStatus.BAD_REQUEST, e.getMessage());
        }
      }
    };
  }

  // deploy helper
  private void deploy(final String namespaceId, final String appId, DeploymentInfo deploymentInfo) throws Exception {
    try {
      Id.Namespace id = Id.Namespace.from(namespaceId);

      Manager<DeploymentInfo, ApplicationWithPrograms> manager = managerFactory.create(new ProgramTerminator() {
        @Override
        public void stop(Id.Namespace id, Id.Program programId, ProgramType type) throws ExecutionException {
          deleteHandler(programId, type);
        }
      });

      manager.deploy(id, appId, deploymentInfo).get();
    } catch (Throwable e) {
      LOG.warn(e.getMessage(), e);
      throw new Exception(e.getMessage());
    }
  }

  private void deleteHandler(Id.Program programId, ProgramType type)
    throws ExecutionException {
    try {
      switch (type) {
        case FLOW:
          stopProgramIfRunning(programId, type);
          break;
        case WORKFLOW:
          scheduler.deleteSchedules(programId, SchedulableProgramType.WORKFLOW);
          break;
        case MAPREDUCE:
          //no-op
          break;
        case SERVICE:
          stopProgramIfRunning(programId, type);
          break;
        case WORKER:
          stopProgramIfRunning(programId, type);
          break;
      }
    } catch (InterruptedException e) {
      throw new ExecutionException(e);
    } catch (SchedulerException e) {
      throw new ExecutionException(e);
    }
  }

  private void stopProgramIfRunning(Id.Program programId, ProgramType type)
    throws InterruptedException, ExecutionException {
    ProgramRuntimeService.RuntimeInfo programRunInfo = findRuntimeInfo(programId.getNamespaceId(),
                                                                       programId.getApplicationId(),
                                                                       programId.getId(),
                                                                       type, runtimeService);
    if (programRunInfo != null) {
      doStop(programRunInfo);
    }
  }

  private void doStop(ProgramRuntimeService.RuntimeInfo runtimeInfo)
    throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(runtimeInfo, UserMessages.getMessage(UserErrors.RUNTIME_INFO_NOT_FOUND));
    ProgramController controller = runtimeInfo.getController();
    controller.stop().get();
  }

  private void getAppDetails(HttpResponder responder, String namespace, String name) {
    try {
      ApplicationSpecification appSpec = store.getApplication(new Id.Application(Id.Namespace.from(namespace), name));
      if (appSpec == null) {
        responder.sendStatus(HttpResponseStatus.NOT_FOUND);
        return;
      }
      ApplicationDetail appDetail = makeAppDetail(appSpec);
      responder.sendJson(HttpResponseStatus.OK, appDetail);
    } catch (SecurityException e) {
      LOG.debug("Security Exception while retrieving app details: ", e);
      responder.sendStatus(HttpResponseStatus.UNAUTHORIZED);
    } catch (Throwable e) {
      LOG.error("Got exception : ", e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private AppFabricServiceStatus removeAll(Id.Namespace identifier) throws Exception {
    List<ApplicationSpecification> allSpecs = new ArrayList<ApplicationSpecification>(
      store.getAllApplications(identifier));

    //Check if any program associated with this namespace is running
    final Id.Namespace accId = Id.Namespace.from(identifier.getId());
    boolean appRunning = runtimeService.checkAnyRunning(new Predicate<Id.Program>() {
      @Override
      public boolean apply(Id.Program programId) {
        return programId.getApplication().getNamespace().equals(accId);
      }
    }, ProgramType.values());

    if (appRunning) {
      return AppFabricServiceStatus.PROGRAM_STILL_RUNNING;
    }

    //All Apps are STOPPED, delete them
    for (ApplicationSpecification appSpec : allSpecs) {
      Id.Application id = Id.Application.from(identifier.getId(), appSpec.getName());
      removeApplication(id);
    }

    return AppFabricServiceStatus.OK;
  }

  private AppFabricServiceStatus removeApplication(final Id.Application appId) throws Exception {
    // Deletion of a particular application is not allowed if that application is used by an adapter
    if (!adapterService.canDeleteApp(appId)) {
      return AppFabricServiceStatus.ADAPTER_CONFLICT;
    }

    //Check if all are stopped.
    boolean appRunning = runtimeService.checkAnyRunning(new Predicate<Id.Program>() {
      @Override
      public boolean apply(Id.Program programId) {
        return programId.getApplication().equals(appId);
      }
    }, ProgramType.values());

    if (appRunning) {
      return AppFabricServiceStatus.PROGRAM_STILL_RUNNING;
    }

    ApplicationSpecification spec = store.getApplication(appId);
    if (spec == null) {
      return AppFabricServiceStatus.PROGRAM_NOT_FOUND;
    }

    //Delete the schedules
    for (WorkflowSpecification workflowSpec : spec.getWorkflows().values()) {
      Id.Program workflowProgramId = Id.Program.from(appId, ProgramType.WORKFLOW, workflowSpec.getName());
      scheduler.deleteSchedules(workflowProgramId, SchedulableProgramType.WORKFLOW);
    }

    deleteMetrics(appId.getNamespaceId(), appId.getId());

    //Delete all preferences of the application and of all its programs
    deletePreferences(appId);

    // Delete all streams and queues state of each flow
    // TODO: This should be unified with the DeletedProgramHandlerStage
    for (FlowSpecification flowSpecification : spec.getFlows().values()) {
      Id.Program flowProgramId = Id.Program.from(appId, ProgramType.FLOW, flowSpecification.getName());

      // Collects stream name to all group ids consuming that stream
      Multimap<String, Long> streamGroups = HashMultimap.create();
      for (FlowletConnection connection : flowSpecification.getConnections()) {
        if (connection.getSourceType() == FlowletConnection.Type.STREAM) {
          long groupId = FlowUtils.generateConsumerGroupId(flowProgramId, connection.getTargetName());
          streamGroups.put(connection.getSourceName(), groupId);
        }
      }
      // Remove all process states and group states for each stream
      String namespace = String.format("%s.%s", flowProgramId.getApplicationId(), flowProgramId.getId());
      for (Map.Entry<String, Collection<Long>> entry : streamGroups.asMap().entrySet()) {
        streamConsumerFactory.dropAll(Id.Stream.from(appId.getNamespaceId(), entry.getKey()),
                                      namespace, entry.getValue());
      }

      queueAdmin.dropAllForFlow(appId.getNamespaceId(), appId.getId(), flowSpecification.getName());
    }
    deleteProgramLocations(appId);

    Location appArchive = store.getApplicationArchiveLocation(appId);
    Preconditions.checkNotNull(appArchive, "Could not find the location of application", appId.getId());
    appArchive.delete();
    store.removeApplication(appId);

    try {
      usageRegistry.unregister(appId);
    } catch (Exception e) {
      LOG.warn("Failed to unregister usage of app: {}", appId, e);
    }

    return AppFabricServiceStatus.OK;
  }

  /**
   * Delete the metrics for an application, or if null is provided as the application ID, for all apps.
   * @param applicationId the application to delete metrics for.
   *                      If null, metrics for all applications in the namespace are deleted.
   */
  private void deleteMetrics(String namespaceId, String applicationId) throws Exception {
    Collection<ApplicationSpecification> applications = Lists.newArrayList();
    if (applicationId == null) {
      applications = this.store.getAllApplications(new Id.Namespace(namespaceId));
    } else {
      ApplicationSpecification spec = this.store.getApplication
        (new Id.Application(new Id.Namespace(namespaceId), applicationId));
      applications.add(spec);
    }

    long endTs = System.currentTimeMillis() / 1000;
    Map<String, String> tags = Maps.newHashMap();
    tags.put(Constants.Metrics.Tag.NAMESPACE, namespaceId);
    for (ApplicationSpecification application : applications) {
      // add or replace application name in the tagMap
      tags.put(Constants.Metrics.Tag.APP, application.getName());
      MetricDeleteQuery deleteQuery = new MetricDeleteQuery(0, endTs, tags);
      metricStore.delete(deleteQuery);
    }
  }


  private Iterable<ProgramSpecification> getProgramSpecs(Id.Application appId) {
    ApplicationSpecification appSpec = store.getApplication(appId);
    return Iterables.concat(appSpec.getFlows().values(),
                            appSpec.getMapReduce().values(),
                            appSpec.getServices().values(),
                            appSpec.getSpark().values(),
                            appSpec.getWorkers().values(),
                            appSpec.getWorkflows().values());
  }

  /**
   * Delete the jar location of the program.
   *
   * @param appId        applicationId.
   * @throws IOException if there are errors with location IO
   */
  private void deleteProgramLocations(Id.Application appId) throws IOException {
    Iterable<ProgramSpecification> programSpecs = getProgramSpecs(appId);
    String appFabricDir = configuration.get(Constants.AppFabric.OUTPUT_DIR);
    for (ProgramSpecification spec : programSpecs) {
      ProgramType type = ProgramTypes.fromSpecification(spec);
      Id.Program programId = Id.Program.from(appId, type, spec.getName());
      try {
        Location location = Programs.programLocation(namespacedLocationFactory, appFabricDir, programId, type);
        location.delete();
      } catch (FileNotFoundException e) {
        LOG.warn("Program jar for program {} not found.", programId.toString(), e);
      }
    }

    // Delete webapp
    // TODO: this will go away once webapp gets a spec
    try {
      Id.Program programId = Id.Program.from(appId.getNamespaceId(), appId.getId(),
                                             ProgramType.WEBAPP, ProgramType.WEBAPP.name().toLowerCase());
      Location location = Programs.programLocation(namespacedLocationFactory, appFabricDir, programId,
                                                   ProgramType.WEBAPP);
      location.delete();
    } catch (FileNotFoundException e) {
      // expected exception when webapp is not present.
    }
  }

  /**
   * Delete stored Preferences of the application and all its programs.
   * @param appId applicationId
   */
  private void deletePreferences(Id.Application appId) {
    Iterable<ProgramSpecification> programSpecs = getProgramSpecs(appId);
    for (ProgramSpecification spec : programSpecs) {

      preferencesStore.deleteProperties(appId.getNamespaceId(), appId.getId(),
                                        ProgramTypes.fromSpecification(spec).getCategoryName(), spec.getName());
      LOG.trace("Deleted Preferences of Program : {}, {}, {}, {}", appId.getNamespaceId(), appId.getId(),
                ProgramTypes.fromSpecification(spec).getCategoryName(), spec.getName());
    }
    preferencesStore.deleteProperties(appId.getNamespaceId(), appId.getId());
    LOG.trace("Deleted Preferences of Application : {}, {}", appId.getNamespaceId(), appId.getId());
  }

  private static ApplicationDetail makeAppDetail(ApplicationSpecification spec) {
    List<ProgramRecord> programs = Lists.newArrayList();
    for (ProgramSpecification programSpec : spec.getFlows().values()) {
      programs.add(new ProgramRecord(ProgramType.FLOW, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getMapReduce().values()) {
      programs.add(new ProgramRecord(ProgramType.MAPREDUCE, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getServices().values()) {
      programs.add(new ProgramRecord(ProgramType.SERVICE, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getSpark().values()) {
      programs.add(new ProgramRecord(ProgramType.SPARK, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getWorkers().values()) {
      programs.add(new ProgramRecord(ProgramType.WORKER, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getWorkflows().values()) {
      programs.add(new ProgramRecord(ProgramType.WORKFLOW, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }

    List<StreamDetail> streams = Lists.newArrayList();
    for (StreamSpecification streamSpec : spec.getStreams().values()) {
      streams.add(new StreamDetail(streamSpec.getName()));
    }

    List<DatasetDetail> datasets = Lists.newArrayList();
    for (DatasetCreationSpec datasetSpec : spec.getDatasets().values()) {
      datasets.add(new DatasetDetail(datasetSpec.getInstanceName(), datasetSpec.getTypeName()));
    }

    return new ApplicationDetail(spec.getName(), spec.getVersion(), spec.getDescription(), streams, datasets, programs);
  }
}
