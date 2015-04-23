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

package co.cask.cdap.client;

import co.cask.cdap.api.templates.AdapterSpecification;
import co.cask.cdap.client.codec.AdapterSpecificationCodec;
import co.cask.cdap.client.config.ClientConfig;
import co.cask.cdap.client.util.RESTClient;
import co.cask.cdap.common.exception.AdapterNotFoundException;
import co.cask.cdap.common.exception.ApplicationTemplateNotFoundException;
import co.cask.cdap.common.exception.BadRequestException;
import co.cask.cdap.common.exception.UnauthorizedException;
import co.cask.cdap.common.utils.Tasks;
import co.cask.cdap.proto.AdapterConfig;
import co.cask.cdap.proto.AdapterStatus;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramRunStatus;
import co.cask.cdap.proto.RunRecord;
import co.cask.common.http.HttpMethod;
import co.cask.common.http.HttpRequest;
import co.cask.common.http.HttpResponse;
import co.cask.common.http.ObjectResponse;
import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Provides ways to interact with CDAP Adapters.
 */
public class AdapterClient {

  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(AdapterSpecification.class, new AdapterSpecificationCodec())
    .create();

  private final RESTClient restClient;
  private final ClientConfig config;

  @Inject
  public AdapterClient(ClientConfig config, RESTClient restClient) {
    this.config = config;
    this.restClient = restClient;
  }

  public AdapterClient(ClientConfig config) {
    this.config = config;
    this.restClient = new RESTClient(config);
  }

  /**
   * Lists all adapters.
   *
   * @return list of {@link co.cask.cdap.api.templates.AdapterSpecification}.
   * @throws java.io.IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   */
  public List<AdapterSpecification> list() throws IOException, UnauthorizedException {
    URL url = config.resolveNamespacedURLV3("adapters");
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken());
    return ObjectResponse.fromJsonBody(response, new TypeToken<List<AdapterSpecification>>() { })
      .getResponseObject();
  }

  /**
   * Gets an adapter.
   *
   * @return an {@link AdapterConfig}.
   * @throws java.io.IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   */
  public AdapterSpecification get(String adapterName)
    throws AdapterNotFoundException, IOException, UnauthorizedException {

    Id.Adapter adapter = Id.Adapter.from(config.getNamespace(), adapterName);
    URL url = config.resolveNamespacedURLV3("adapters/" + adapterName);
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new AdapterNotFoundException(adapter);
    }
    return ObjectResponse.fromJsonBody(response, new TypeToken<AdapterSpecification>() { }).getResponseObject();
  }

  /**
   * Creates an adapter.
   *
   * @param adapterName name of the adapter to create
   * @param adapterSpec properties of the adapter to create
   * @throws ApplicationTemplateNotFoundException if the desired adapter type was not found
   * @throws BadRequestException if the provided {@link AdapterConfig} was bad
   * @throws IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   */
  public void create(String adapterName, AdapterConfig adapterSpec)
    throws ApplicationTemplateNotFoundException, BadRequestException, IOException, UnauthorizedException {

    URL url = config.resolveNamespacedURLV3(String.format("adapters/%s", adapterName));
    HttpRequest request = HttpRequest.put(url).withBody(GSON.toJson(adapterSpec)).build();

    HttpResponse response = restClient.execute(request, config.getAccessToken(), HttpURLConnection.HTTP_NOT_FOUND,
                                               HttpURLConnection.HTTP_BAD_REQUEST);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new ApplicationTemplateNotFoundException(Id.ApplicationTemplate.from(adapterSpec.getTemplate()));
    } else if (response.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
      throw new BadRequestException(response.getResponseMessage());
    }
  }

  /**
   * Deletes an adapter.
   *
   * @param adapterName Name of the adapter to delete
   * @throws AdapterNotFoundException if the adapter with the specified name could not be found
   * @throws java.io.IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   */
  public void delete(String adapterName) throws AdapterNotFoundException, IOException, UnauthorizedException {
    Id.Adapter adapter = Id.Adapter.from(config.getNamespace(), adapterName);
    URL url = config.resolveNamespacedURLV3(String.format("adapters/%s", adapterName));
    HttpResponse response = restClient.execute(HttpMethod.DELETE, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new AdapterNotFoundException(adapter);
    }
  }

  /**
   * Checks if a adapter exists.
   *
   * @param adapterName Name of the adapter to check
   * @return true if the adapter exists
   * @throws java.io.IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   */
  public boolean exists(String adapterName) throws IOException, UnauthorizedException {
    URL url = config.resolveNamespacedURLV3(String.format("adapters/%s", adapterName));
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    return response.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND;
  }

  /**
   * Waits for an adapter to exist.
   *
   * @param adapterName Name of the adapter to check
   * @param timeout time to wait before timing out
   * @param timeoutUnit time unit of timeout
   * @throws IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   * @throws TimeoutException if the adapter was not yet existent before {@code timeout} milliseconds
   * @throws InterruptedException if interrupted while waiting
   */
  public void waitForExists(final String adapterName, long timeout, TimeUnit timeoutUnit)
    throws IOException, UnauthorizedException, TimeoutException, InterruptedException {

    try {
      Tasks.waitFor(true, new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return exists(adapterName);
        }
      }, timeout, timeoutUnit, 1, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwables.propagateIfPossible(e.getCause(), IOException.class, UnauthorizedException.class);
    }
  }

  /**
   * Waits for an adapter to be deleted.
   *
   * @param adapterName Name of the adapter to check
   * @param timeout time to wait before timing out
   * @param timeoutUnit time unit of timeout
   * @throws IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   * @throws TimeoutException if the adapter was not yet deleted before {@code timeout} milliseconds
   * @throws InterruptedException if interrupted while waiting
   */
  public void waitForDeleted(final String adapterName, long timeout, TimeUnit timeoutUnit)
    throws IOException, UnauthorizedException, TimeoutException, InterruptedException {

    try {
      Tasks.waitFor(false, new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return exists(adapterName);
        }
      }, timeout, timeoutUnit, 1, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwables.propagateIfPossible(e.getCause(), IOException.class, UnauthorizedException.class);
    }
  }

  /**
   * Starts an adapter.
   *
   * @param adapterName the name of the adapter to start
   * @throws IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   * @throws AdapterNotFoundException if the adapter is not found
   */
  public void start(String adapterName) throws IOException, UnauthorizedException, AdapterNotFoundException {
    Id.Adapter adapter = Id.Adapter.from(config.getNamespace(), adapterName);
    URL url = config.resolveNamespacedURLV3(String.format("adapters/%s/start", adapterName));
    HttpResponse response = restClient.execute(HttpMethod.POST, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new AdapterNotFoundException(adapter);
    }
  }

  /**
   * Stops an adapter.
   *
   * @param adapterName the name of the adapter to stop
   * @throws IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   * @throws AdapterNotFoundException if the adapter is not found
   */
  public void stop(String adapterName) throws IOException, UnauthorizedException, AdapterNotFoundException {
    Id.Adapter adapter = Id.Adapter.from(config.getNamespace(), adapterName);
    URL url = config.resolveNamespacedURLV3(String.format("adapters/%s/stop", adapterName));
    HttpResponse response = restClient.execute(HttpMethod.POST, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new AdapterNotFoundException(adapter);
    }
  }


  /*

  @GET
  @Path("/namespaces/{namespace-id}/adapters/{adapter-id}/logs")
  public void getAdapterLogs(HttpRequest request, HttpResponder responder,
                             @PathParam("namespace-id") String namespaceId,
                             @PathParam("adapter-id") String adapterId,
                             // The following 3 query params should be removed,
                             // when we have a way of querying adapter service
                             // for the template and program, given an adapter name
                             @QueryParam("template") String templateId,
                             @QueryParam("programtype") String programType,
                             @QueryParam("programid") String programId,
                             @QueryParam("start") @DefaultValue("-1") long fromTimeMsParam,
                             @QueryParam("stop") @DefaultValue(Long.MAX_VALUE + "") long toTimeMsParam,
                             @QueryParam("escape") @DefaultValue("true") boolean escape,
                             @QueryParam("filter") @DefaultValue("") String filterStr) {
   */

  public String getLogs(String adapterName)
    throws AdapterNotFoundException, UnauthorizedException, IOException {
    return getLogs(adapterName, null, null, null, null);
  }

  public String getLogs(String adapterName, @Nullable Long start, @Nullable Long stop)
    throws AdapterNotFoundException, UnauthorizedException, IOException {
    return getLogs(adapterName, start, stop, null, null);
  }

  public String getLogs(String adapterName, @Nullable Long start, @Nullable Long stop,
                        @Nullable Boolean escape, @Nullable String filter)
    throws IOException, AdapterNotFoundException, UnauthorizedException {

    AdapterSpecification spec = get(adapterName);
    // TODO: use response from get(adapterName) to determine Id.Program
    // TODO: complete
    return "";
  }

  public AdapterStatus getStatus(String adapterName)
    throws IOException, UnauthorizedException, AdapterNotFoundException {

    Id.Adapter adapter = Id.Adapter.from(config.getNamespace(), adapterName);
    URL url = config.resolveNamespacedURLV3(String.format("adapters/%s/status", adapterName));
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new AdapterNotFoundException(adapter);
    }

    return ObjectResponse.fromJsonBody(response, AdapterStatus.class).getResponseObject();
  }

  public List<RunRecord> getRuns(String adapterName, ProgramRunStatus status, long startTs, long endTs,
                                 @Nullable Integer resultLimit)
    throws IOException, UnauthorizedException, AdapterNotFoundException {

    String query = "?status" + status + "&startTs=" + startTs + "&endTs=" + endTs +
      (resultLimit == null ? "" : "&resultLimit=" + resultLimit);

    Id.Adapter adapter = Id.Adapter.from(config.getNamespace(), adapterName);
    URL url = config.resolveNamespacedURLV3(String.format("adapters/%s/runs" + query, adapterName));
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new AdapterNotFoundException(adapter);
    }

    return ObjectResponse.<List<RunRecord>>fromJsonBody(
      response, new TypeToken<List<RunRecord>>() { }.getType()).getResponseObject();
  }
}
