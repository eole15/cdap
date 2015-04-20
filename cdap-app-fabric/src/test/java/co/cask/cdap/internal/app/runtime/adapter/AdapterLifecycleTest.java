/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.internal.app.runtime.adapter;

import co.cask.cdap.AppWithServices;
import co.cask.cdap.DummyBatchTemplate;
import co.cask.cdap.DummyWorkerTemplate;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.io.Locations;
import co.cask.cdap.internal.app.services.http.AppFabricTestBase;
import co.cask.cdap.internal.test.AppJarHelper;
import co.cask.cdap.proto.AdapterConfig;
import co.cask.cdap.templates.AdapterSpecification;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * AdapterService life cycle tests.
 */
public class AdapterLifecycleTest extends AppFabricTestBase {
  private static final Gson GSON = new Gson();
  private static final Type ADAPTER_SPEC_LIST_TYPE =
    new TypeToken<List<AdapterSpecification>>() { }.getType();
  private static LocationFactory locationFactory;
  private static File adapterDir;
  private static AdapterService adapterService;

  @BeforeClass
  public static void setup() throws Exception {
    CConfiguration conf = getInjector().getInstance(CConfiguration.class);
    locationFactory = getInjector().getInstance(LocationFactory.class);
    adapterDir = new File(conf.get(Constants.AppFabric.APP_TEMPLATE_DIR));
    setupAdapter(DummyBatchTemplate.class);
    setupAdapter(DummyWorkerTemplate.class);
    adapterService = getInjector().getInstance(AdapterService.class);
    // this is called here because the service is already started by the test base at this point
    adapterService.registerTemplates();
  }

  @AfterClass
  public static void tearDown() throws Exception {
    adapterService.stop();
  }

  @Test
  public void testRealtimeAdapterLifeCycle() throws Exception {
    String namespaceId = Constants.DEFAULT_NAMESPACE;
    String adapterName = "realtimeAdapter";
    String templateId = DummyWorkerTemplate.NAME;
    DummyWorkerTemplate.Config config = new DummyWorkerTemplate.Config(2);
    AdapterConfig adapterConfig = new AdapterConfig("description", DummyWorkerTemplate.NAME, GSON.toJsonTree(config));
    testAdapterLifeCycle(namespaceId, templateId, adapterName, adapterConfig);
  }

  @Test
  public void testBatchAdapterLifeCycle() throws Exception {
    String namespaceId = Constants.DEFAULT_NAMESPACE;
    String adapterName = "myStreamConverter";
    String templateId = DummyBatchTemplate.NAME;
    DummyBatchTemplate.Config config = new DummyBatchTemplate.Config("somesource", "0 0 1 1 *");
    AdapterConfig adapterConfig = new AdapterConfig("description", DummyBatchTemplate.NAME, GSON.toJsonTree(config));
    testAdapterLifeCycle(namespaceId, templateId, adapterName, adapterConfig);
  }

  private void testAdapterLifeCycle(String namespaceId, String templateId, String adapterName,
                                    AdapterConfig adapterConfig) throws Exception {
    String deleteURL = getVersionedAPIPath("apps/" + templateId, Constants.Gateway.API_VERSION_3_TOKEN, namespaceId);
    HttpResponse response = createAdapter(namespaceId, adapterName, adapterConfig);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // A duplicate create request (or any other create request with the same namespace + adapterName) will result in 409
    response = createAdapter(namespaceId, adapterName, adapterConfig);
    Assert.assertEquals(409, response.getStatusLine().getStatusCode());

    response = listAdapters(namespaceId);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    List<AdapterSpecification> list = readResponse(response, ADAPTER_SPEC_LIST_TYPE);
    Assert.assertEquals(1, list.size());
    checkIsExpected(adapterConfig, list.get(0));

    response = getAdapter(namespaceId, adapterName);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    AdapterSpecification receivedAdapterConfig = readResponse(response, AdapterSpecification.class);
    checkIsExpected(adapterConfig, receivedAdapterConfig);

    List<JsonObject> deployedApps = getAppList(namespaceId);
    Assert.assertEquals(1, deployedApps.size());
    JsonObject deployedApp = deployedApps.get(0);
    Assert.assertEquals(templateId, deployedApp.get("id").getAsString());

    response = getAdapterStatus(namespaceId, adapterName);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    String status = readResponse(response);
    Assert.assertEquals("STOPPED", status);

    response = startStopAdapter(namespaceId, adapterName, "stop");
    Assert.assertEquals(409, response.getStatusLine().getStatusCode());

    response = startStopAdapter(namespaceId, adapterName, "start");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    response = getAdapterStatus(namespaceId, adapterName);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    status = readResponse(response);
    Assert.assertEquals("STARTED", status);

    // Deleting App should fail
    deleteApplication(1, deleteURL, 403);

    response = deleteAdapter(namespaceId, adapterName);
    Assert.assertEquals(403, response.getStatusLine().getStatusCode());

    response = startStopAdapter(namespaceId, adapterName, "stop");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // Deleting App should fail
    deleteApplication(1, deleteURL, 403);

    response = deleteAdapter(namespaceId, adapterName);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    response = getAdapter(namespaceId, adapterName);
    Assert.assertEquals(404, response.getStatusLine().getStatusCode());

    // delete the application
    deleteApplication(60, deleteURL, 200);
    deployedApps = getAppList(namespaceId);
    Assert.assertTrue(deployedApps.isEmpty());

    // Check if we are able to deploy Adapter after Template app is deleted
    response = createAdapter(namespaceId, adapterName, adapterConfig);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // Delete Adpater
    response = deleteAdapter(namespaceId, adapterName);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // delete the application
    deleteApplication(60, deleteURL, 200);
    deployedApps = getAppList(namespaceId);
    Assert.assertTrue(deployedApps.isEmpty());
  }

  private void checkIsExpected(AdapterConfig config, AdapterSpecification spec) {
    Assert.assertEquals(config.getDescription(), spec.getDescription());
    Assert.assertEquals(config.getTemplate(), spec.getTemplate());
    Assert.assertEquals(config.getConfig(), spec.getConfig());
  }

  @Test
  public void testRestrictUserApps() throws Exception {
    // Testing that users can not deploy an application
    HttpResponse response = deploy(AppWithServices.class, Constants.Gateway.API_VERSION_3_TOKEN,
      Constants.DEFAULT_NAMESPACE, DummyBatchTemplate.NAME);
    Assert.assertEquals(400, response.getStatusLine().getStatusCode());
    String responseString = readResponse(response);
    Assert.assertTrue(String.format("Response String: %s", responseString),
                      responseString.contains("An ApplicationTemplate exists with a conflicting name."));
  }

  @Test
  public void testMissingTemplateReturns404() throws Exception {
    Map<String, Object> config = ImmutableMap.<String, Object>of("field1", "someval", "field2", "otherval");
    AdapterConfig badConfig = new AdapterConfig("description", "badtemplate", GSON.toJsonTree(config));
    HttpResponse response = createAdapter(Constants.DEFAULT_NAMESPACE, "badAdapter", badConfig);
    Assert.assertEquals(404, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testInvalidJsonBodyReturns400() throws Exception {
    HttpResponse response = doPut(
      String.format("%s/namespaces/%s/adapters/%s",
                    Constants.Gateway.API_VERSION_3, Constants.DEFAULT_NAMESPACE, "myadapter"), "[]");
    Assert.assertEquals(400, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testNoTemplateFieldReturns400() throws Exception {
    HttpResponse response = doPut(
      String.format("%s/namespaces/%s/adapters/%s",
                    Constants.Gateway.API_VERSION_3, Constants.DEFAULT_NAMESPACE, "myadapter"), "{}");
    Assert.assertEquals(400, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testInvalidConfigReturns400() throws Exception {
    String adapterName = "badConfigAdapter";
    DummyBatchTemplate.Config config = new DummyBatchTemplate.Config("somesource", null);
    AdapterConfig adapterConfig = new AdapterConfig("description", DummyBatchTemplate.NAME, GSON.toJsonTree(config));
    HttpResponse response = doPut(
      String.format("%s/namespaces/%s/adapters/%s",
        Constants.Gateway.API_VERSION_3, Constants.DEFAULT_NAMESPACE, adapterName), GSON.toJson(adapterConfig));
    Assert.assertEquals(400, response.getStatusLine().getStatusCode());

    String deleteURL = getVersionedAPIPath("apps/" + DummyBatchTemplate.NAME,
      Constants.Gateway.API_VERSION_3_TOKEN, Constants.DEFAULT_NAMESPACE);
    deleteApplication(60, deleteURL, 200);
  }

  @Test
  public void testGetAllAdapters() throws Exception {
    // create first adapter
    String namespaceId = Constants.DEFAULT_NAMESPACE;
    String adapter1 = "adapter1";
    DummyWorkerTemplate.Config config1 = new DummyWorkerTemplate.Config(2);
    createAdapter(namespaceId, adapter1,
                  new AdapterConfig("description", DummyWorkerTemplate.NAME, GSON.toJsonTree(config1)));

    // create second adapter with a different template
    String adapter2 = "adapter2";
    DummyBatchTemplate.Config config2 = new DummyBatchTemplate.Config("somesource", "0 0 1 1 *");
    createAdapter(namespaceId, adapter2,
      new AdapterConfig("description", DummyBatchTemplate.NAME, GSON.toJsonTree(config2)));

    // test get all endpoint
    HttpResponse response = listAdapters(namespaceId);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    List<AdapterSpecification> list = readResponse(response, ADAPTER_SPEC_LIST_TYPE);
    Assert.assertEquals(2, list.size());

    // test get all for a template
    response = listAdaptersForTemplate(namespaceId, DummyWorkerTemplate.NAME);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    list = readResponse(response, ADAPTER_SPEC_LIST_TYPE);
    Assert.assertEquals(1, list.size());
    Assert.assertEquals(adapter1, list.get(0).getName());

    response = listAdaptersForTemplate(namespaceId, DummyBatchTemplate.NAME);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    list = readResponse(response, ADAPTER_SPEC_LIST_TYPE);
    Assert.assertEquals(1, list.size());
    Assert.assertEquals(adapter2, list.get(0).getName());

    // delete adapters
    deleteAdapter(namespaceId, adapter1);
    deleteAdapter(namespaceId, adapter2);
    deleteApplication(60, getVersionedAPIPath("apps/" + DummyBatchTemplate.NAME,
      Constants.Gateway.API_VERSION_3_TOKEN, Constants.DEFAULT_NAMESPACE), 200);
    deleteApplication(60, getVersionedAPIPath("apps/" + DummyWorkerTemplate.NAME,
      Constants.Gateway.API_VERSION_3_TOKEN, Constants.DEFAULT_NAMESPACE), 200);

    // check there are none
    response = listAdapters(namespaceId);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    list = readResponse(response, ADAPTER_SPEC_LIST_TYPE);
    Assert.assertTrue(list.isEmpty());
  }

  private static void setupAdapter(Class<?> clz) throws IOException {
    Location adapterJar = AppJarHelper.createDeploymentJar(locationFactory, clz);
    File destination =  new File(String.format("%s/%s", adapterDir.getAbsolutePath(), adapterJar.getName()));
    Files.copy(Locations.newInputSupplier(adapterJar), destination);
  }

  private HttpResponse createAdapter(String namespaceId, String name, AdapterConfig config) throws Exception {
    return doPut(String.format("%s/namespaces/%s/adapters/%s",
                               Constants.Gateway.API_VERSION_3, namespaceId, name), GSON.toJson(config));
  }

  private HttpResponse getAdapterStatus(String namespaceId, String name) throws Exception {
    return doGet(String.format("%s/namespaces/%s/adapters/%s/status",
                               Constants.Gateway.API_VERSION_3, namespaceId, name));
  }

  private HttpResponse listAdapters(String namespaceId) throws Exception {
    return doGet(String.format("%s/namespaces/%s/adapters",
                               Constants.Gateway.API_VERSION_3, namespaceId));
  }

  private HttpResponse listAdaptersForTemplate(String namespaceId, String template) throws Exception {
    return doGet(String.format("%s/namespaces/%s/adapters?template=%s",
                               Constants.Gateway.API_VERSION_3, namespaceId, template));
  }

  private HttpResponse getAdapter(String namespaceId, String adapterId) throws Exception {
    return doGet(String.format("%s/namespaces/%s/adapters/%s",
                               Constants.Gateway.API_VERSION_3, namespaceId, adapterId));
  }

  private HttpResponse startStopAdapter(String namespaceId, String adapterId, String action) throws Exception {
    return doPost(String.format("%s/namespaces/%s/adapters/%s/%s",
                                Constants.Gateway.API_VERSION_3, namespaceId, adapterId, action));
  }

  private HttpResponse deleteAdapter(String namespaceId, String adapterId) throws Exception {
    return doDelete(String.format("%s/namespaces/%s/adapters/%s",
                                  Constants.Gateway.API_VERSION_3, namespaceId, adapterId));
  }
}
