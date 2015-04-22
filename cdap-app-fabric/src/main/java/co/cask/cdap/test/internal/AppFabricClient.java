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

package co.cask.cdap.test.internal;

import co.cask.cdap.api.schedule.ScheduleSpecification;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.gateway.handlers.AppLifecycleHttpHandler;
import co.cask.cdap.gateway.handlers.NamespaceHttpHandler;
import co.cask.cdap.gateway.handlers.ProgramLifecycleHttpHandler;
import co.cask.cdap.gateway.handlers.WorkflowHttpHandler;
import co.cask.cdap.internal.app.BufferFileInputStream;
import co.cask.cdap.internal.app.namespace.NamespaceAdmin;
import co.cask.cdap.internal.test.AppJarHelper;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.Instances;
import co.cask.cdap.proto.NamespaceMeta;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.RunRecord;
import co.cask.cdap.proto.ServiceInstances;
import co.cask.cdap.proto.codec.ScheduleSpecificationCodec;
import co.cask.http.BodyConsumer;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Client tool for AppFabricHttpHandler.
 */
public class AppFabricClient {
  private static final Logger LOG = LoggerFactory.getLogger(AppFabricClient.class);
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(ScheduleSpecification.class, new ScheduleSpecificationCodec())
    .create();

  private final LocationFactory locationFactory;
  private final AppLifecycleHttpHandler appLifecycleHttpHandler;
  private final ProgramLifecycleHttpHandler programLifecycleHttpHandler;
  private final WorkflowHttpHandler workflowHttpHandler;
  private final NamespaceHttpHandler namespaceHttpHandler;
  private final NamespaceAdmin namespaceAdmin;

  @Inject
  public AppFabricClient(LocationFactory locationFactory,
                         AppLifecycleHttpHandler appLifecycleHttpHandler,
                         ProgramLifecycleHttpHandler programLifecycleHttpHandler,
                         NamespaceHttpHandler namespaceHttpHandler,
                         NamespaceAdmin namespaceAdmin, WorkflowHttpHandler workflowHttpHandler) {
    this.locationFactory = locationFactory;
    this.appLifecycleHttpHandler = appLifecycleHttpHandler;
    this.programLifecycleHttpHandler = programLifecycleHttpHandler;
    this.namespaceHttpHandler = namespaceHttpHandler;
    this.namespaceAdmin = namespaceAdmin;
    this.workflowHttpHandler = workflowHttpHandler;
  }

  public void reset() throws Exception {
    MockResponder responder;
    HttpRequest request;

    // delete all namespaces
    for (NamespaceMeta namespaceMeta : namespaceAdmin.listNamespaces()) {
      Id.Namespace namespace = Id.Namespace.from(namespaceMeta.getName());

      responder = new MockResponder();
      request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE,
                                       String.format("/v3/unrecoverable/namespaces/%s/datasets", namespace.getId()));
      namespaceHttpHandler.deleteDatasets(request, responder, namespaceMeta.getName());
      verifyResponse(HttpResponseStatus.OK, responder.getStatus(),
                     String.format("could not delete datasets in namespace '%s'", namespace.getId()));

      responder = new MockResponder();
      request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE,
                                       String.format("/v3/unrecoverable/namespaces/%s", namespace.getId()));
      namespaceHttpHandler.delete(request, responder, namespaceMeta.getName());
      verifyResponse(HttpResponseStatus.OK, responder.getStatus(),
                     String.format("could not delete namespace '%s'", namespace.getId()));
    }
  }

  public void startProgram(String namespaceId, String appId,
                           String flowId, ProgramType type, Map<String, String> args) {

    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/%s/%s/start",
                               namespaceId, appId, type.getCategoryName(), flowId);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
    String argString = GSON.toJson(args);
    if (argString != null) {
      request.setContent(ChannelBuffers.wrappedBuffer(argString.getBytes(Charsets.UTF_8)));
    }
    programLifecycleHttpHandler.performAction(request, responder, namespaceId, appId,
                                              type.getCategoryName(), flowId, "start");
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Start " + type + " failed");
  }

  public void stopProgram(String namespaceId, String appId, String flowId, ProgramType type) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/%s/%s/stop",
                               namespaceId, appId, type.getCategoryName(), flowId);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
    programLifecycleHttpHandler.performAction(request, responder, namespaceId, appId,
                                              type.getCategoryName(), flowId, "stop");
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Stop " + type + " failed");
  }

  public String getStatus(String namespaceId, String appId, String flowId, ProgramType type) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/%s/%s/status", namespaceId, appId, type, flowId);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
    programLifecycleHttpHandler.getStatus(request, responder, namespaceId, appId, type.getCategoryName(), flowId);
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Get status " + type + " failed");
    Map<String, String> json = responder.decodeResponseContent(new TypeToken<Map<String, String>>() { });
    return json.get("status");
  }

  public void setWorkerInstances(String namespaceId, String appId, String workerId, int instances) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/worker/%s/instances", namespaceId, appId, workerId);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, uri);
    JsonObject json = new JsonObject();
    json.addProperty("instances", instances);
    request.setContent(ChannelBuffers.wrappedBuffer(json.toString().getBytes()));
    programLifecycleHttpHandler.setWorkerInstances(request, responder, namespaceId, appId, workerId);
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Set worker instances failed");
  }

  public Instances getWorkerInstances(String namespaceId, String appId, String workerId) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/worker/%s/instances", namespaceId, appId, workerId);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    programLifecycleHttpHandler.getWorkerInstances(request, responder, namespaceId, appId, workerId);
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Get worker instances failed");
    return responder.decodeResponseContent(new TypeToken<Instances>() { });
  }

  public void setServiceInstances(String namespaceId, String applicationId, String serviceName, int instances) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/services/%s/instances",
                               namespaceId, applicationId, serviceName);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, uri);
    JsonObject json = new JsonObject();
    json.addProperty("instances", instances);
    request.setContent(ChannelBuffers.wrappedBuffer(json.toString().getBytes()));
    programLifecycleHttpHandler.setServiceInstances(request, responder, namespaceId, applicationId, serviceName);
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Set service instances failed");
  }

  public ServiceInstances getServiceInstances(String namespaceId, String applicationId, String serviceName) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/services/%s/instances",
                               namespaceId, applicationId, serviceName);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    programLifecycleHttpHandler.getServiceInstances(request, responder, namespaceId, applicationId, serviceName);
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Get service instances failed");
    return responder.decodeResponseContent(new TypeToken<ServiceInstances>() { });
  }

  public void setFlowletInstances(String namespaceId, String applicationId,
                                  String flowId, String flowletName, int instances) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/flows/%s/flowlets/%s/instances/%s",
                               namespaceId, applicationId, flowId, flowletName, instances);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, uri);
    JsonObject json = new JsonObject();
    json.addProperty("instances", instances);
    request.setContent(ChannelBuffers.wrappedBuffer(json.toString().getBytes()));
    programLifecycleHttpHandler.setFlowletInstances(request, responder, namespaceId,
                                                    applicationId, flowId, flowletName);
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Set flowlet instances failed");
  }

  public List<ScheduleSpecification> getSchedules(String namespaceId, String appId, String wflowId) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/workflows/%s/schedules", namespaceId, appId, wflowId);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    workflowHttpHandler.getWorkflowSchedules(request, responder, namespaceId, appId, wflowId);

    List<ScheduleSpecification> schedules = responder.decodeResponseContent(
      new TypeToken<List<ScheduleSpecification>>() { }, GSON);
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Getting workflow schedules failed");
    return schedules;
  }

  public List<RunRecord> getHistory(String namespaceId, String appId, String wflowId) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/workflows/%s/runs?status=completed",
                               namespaceId, appId, wflowId);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    programLifecycleHttpHandler.programHistory(request, responder, namespaceId, appId,
                                               "workflows", wflowId, null, null, null, 100);
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Getting workflow history failed");

    return responder.decodeResponseContent(new TypeToken<List<RunRecord>>() { });
  }

  public void suspend(String namespaceId, String appId, String scheduleName) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/schedules/%s/suspend", namespaceId, appId, scheduleName);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    programLifecycleHttpHandler.performAction(request, responder, namespaceId, appId,
                                              "schedules", scheduleName, "suspend");
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Suspend workflow schedules failed");
  }

  public void resume(String namespaceId, String appId, String schedName) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/schedules/%s/resume", namespaceId, appId, schedName);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    programLifecycleHttpHandler.performAction(request, responder, namespaceId, appId,
                                              "schedules", schedName, "resume");
    verifyResponse(HttpResponseStatus.OK, responder.getStatus(), "Resume workflow schedules failed");
  }

  public String scheduleStatus(String namespaceId, String appId, String schedId, int expectedResponseCode) {
    MockResponder responder = new MockResponder();
    String uri = String.format("/v3/namespaces/%s/apps/%s/schedules/%s/status", namespaceId, appId, schedId);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    programLifecycleHttpHandler.getStatus(request, responder, namespaceId, appId, "schedules", schedId);
    verifyResponse(HttpResponseStatus.valueOf(expectedResponseCode), responder.getStatus(),
                   "Get schedules status failed");
    if (HttpResponseStatus.NOT_FOUND.getCode() == expectedResponseCode) {
      return "NOT_FOUND";
    } else {
      Map<String, String> json = responder.decodeResponseContent(new TypeToken<Map<String, String>>() { });
      return json.get("status");
    }
  }

  private void verifyResponse(HttpResponseStatus expected, HttpResponseStatus actual, String errorMsg) {
    if (!expected.equals(actual)) {
      throw new IllegalStateException(String.format("Expected %s, got %s. Error: %s",
                                                    expected, actual, errorMsg));
    }
  }

  public Location deployApplication(Id.Namespace namespace, String appName,
                                    Class<?> applicationClz, File...bundleEmbeddedJars) throws Exception {

    Preconditions.checkNotNull(applicationClz, "Application cannot be null.");

    Location deployedJar = AppJarHelper.createDeploymentJar(locationFactory, applicationClz, bundleEmbeddedJars);
    LOG.info("Created deployedJar at {}", deployedJar.toURI().toASCIIString());

    String archiveName = appName + ".jar";
    DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                                                        String.format("/v3/namespaces/%s/apps", namespace.getId()));
    request.setHeader(Constants.Gateway.API_KEY, "api-key-example");
    request.setHeader("X-Archive-Name", archiveName);
    MockResponder mockResponder = new MockResponder();
    BodyConsumer bodyConsumer = appLifecycleHttpHandler.deploy(request, mockResponder, namespace.getId(), archiveName);
    Preconditions.checkNotNull(bodyConsumer, "BodyConsumer from deploy call should not be null");

    BufferFileInputStream is = new BufferFileInputStream(deployedJar.getInputStream(), 100 * 1024);
    try {
      byte[] chunk = is.read();
      while (chunk.length > 0) {
        mockResponder = new MockResponder();
        bodyConsumer.chunk(ChannelBuffers.wrappedBuffer(chunk), mockResponder);
        Preconditions.checkState(mockResponder.getStatus() == null, "failed to deploy app");
        chunk = is.read();
      }
      mockResponder = new MockResponder();
      bodyConsumer.finished(mockResponder);
      verifyResponse(HttpResponseStatus.OK, mockResponder.getStatus(), "Failed to deploy app");
    } catch (Exception e) {
      throw Throwables.propagate(e);
    } finally {
      is.close();
    }
    return deployedJar;
  }

}
