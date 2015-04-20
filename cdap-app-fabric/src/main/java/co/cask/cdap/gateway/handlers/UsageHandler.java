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

package co.cask.cdap.gateway.handlers;

import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.registry.UsageDataset;
import co.cask.cdap.data2.registry.UsageDatasetUtil;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramType;
import co.cask.http.AbstractHttpHandler;
import co.cask.http.HttpResponder;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * The {@link co.cask.http.HttpHandler} for handling REST calls to the usage registry.
 */
@Path(Constants.Gateway.API_VERSION_3)
public class UsageHandler extends AbstractHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(UsageHandler.class);

  private final Supplier<UsageDataset> usageDataset;

  @Inject
  public UsageHandler(final DatasetFramework datasetFramework) {
    this.usageDataset = Suppliers.memoize(new Supplier<UsageDataset>() {
      @Override
      public UsageDataset get() {
        try {
          return new UsageDatasetUtil(datasetFramework).getUsageDataset();
        } catch (Exception e) {
          throw Throwables.propagate(e);
        }
      }
    });
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/datasets")
  public void getAppDatasetUsage(HttpRequest request, HttpResponder responder,
                                 @PathParam("namespace-id") String namespaceId,
                                 @PathParam("app-id") String appId) {
    Id.Application id = Id.Application.from(namespaceId, appId);
    Set<? extends Id> ids = usageDataset.get().getDatasets(id);
    responder.sendJson(HttpResponseStatus.OK, ids);
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/streams")
  public void getAppStreamUsage(HttpRequest request, HttpResponder responder,
                                @PathParam("namespace-id") String namespaceId,
                                @PathParam("app-id") String appId) {
    Id.Application id = Id.Application.from(namespaceId, appId);
    Set<? extends Id> ids = usageDataset.get().getStreams(id);
    responder.sendJson(HttpResponseStatus.OK, ids);
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/{program-type}/{program-id}/datasets")
  public void geProgramDatasetUsage(HttpRequest request, HttpResponder responder,
                                    @PathParam("namespace-id") String namespaceId,
                                    @PathParam("app-id") String appId,
                                    @PathParam("program-type") String programType,
                                    @PathParam("program-id") String programId) {
    ProgramType type = ProgramType.valueOfCategoryName(programType);
    Id.Program id = Id.Program.from(namespaceId, appId, type, programId);
    Set<? extends Id> ids = usageDataset.get().getDatasets(id);
    responder.sendJson(HttpResponseStatus.OK, ids);
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/{program-type}/{program-id}/streams")
  public void getProgramStreamUsage(HttpRequest request, HttpResponder responder,
                                    @PathParam("namespace-id") String namespaceId,
                                    @PathParam("app-id") String appId,
                                    @PathParam("program-type") String programType,
                                    @PathParam("program-id") String programId) {
    ProgramType type = ProgramType.valueOfCategoryName(programType);
    Id.Program id = Id.Program.from(namespaceId, appId, type, programId);
    Set<? extends Id> ids = usageDataset.get().getStreams(id);
    responder.sendJson(HttpResponseStatus.OK, ids);
  }

  @GET
  @Path("/namespaces/{namespace-id}/adapters/{adapter-id}/datasets")
  public void getAdapterDatasetUsage(HttpRequest request, HttpResponder responder,
                                     @PathParam("namespace-id") String namespaceId,
                                     @PathParam("adapter-id") String adapterId) {
    Id.Adapter id = Id.Adapter.from(namespaceId, adapterId);
    Set<? extends Id> ids = usageDataset.get().getDatasets(id);
    responder.sendJson(HttpResponseStatus.OK, ids);
  }

  @GET
  @Path("/namespaces/{namespace-id}/adapters/{adapter-id}/streams")
  public void getAdapterStreamUsage(HttpRequest request, HttpResponder responder,
                                    @PathParam("namespace-id") String namespaceId,
                                    @PathParam("adapter-id") String adapterId) {
    Id.Adapter id = Id.Adapter.from(namespaceId, adapterId);
    Set<? extends Id> ids = usageDataset.get().getStreams(id);
    responder.sendJson(HttpResponseStatus.OK, ids);
  }

  @GET
  @Path("/namespaces/{namespace-id}/streams/{stream-id}/programs")
  public void getStreamProgramUsage(HttpRequest request, HttpResponder responder,
                                @PathParam("namespace-id") String namespaceId,
                                @PathParam("stream-id") String streamId) {
    Id.Stream id = Id.Stream.from(namespaceId, streamId);
    Set<? extends Id> ids = usageDataset.get().getPrograms(id);
    responder.sendJson(HttpResponseStatus.OK, ids);
  }

  @GET
  @Path("/namespaces/{namespace-id}/streams/{stream-id}/adapters")
  public void getStreamAdapterUsage(HttpRequest request, HttpResponder responder) {

  }

  @GET
  @Path("/namespaces/{namespace-id}/data/datasets/{dataset-id}/apps")
  public void getDatasetAppUsage(HttpRequest request, HttpResponder responder) {

  }

  @GET
  @Path("/namespaces/{namespace-id}/data/datasets/{dataset-id}/adapters")
  public void getDatasetAdapterUsage(HttpRequest request, HttpResponder responder) {

  }
}
