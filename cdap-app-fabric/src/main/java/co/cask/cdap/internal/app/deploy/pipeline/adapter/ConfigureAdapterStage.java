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

package co.cask.cdap.internal.app.deploy.pipeline.adapter;

import co.cask.cdap.app.deploy.ConfigResponse;
import co.cask.cdap.internal.app.ApplicationSpecificationAdapter;
import co.cask.cdap.internal.app.deploy.InMemoryAdapterConfigurator;
import co.cask.cdap.internal.app.deploy.InMemoryConfigurator;
import co.cask.cdap.internal.app.deploy.pipeline.ApplicationDeployable;
import co.cask.cdap.pipeline.AbstractStage;
import co.cask.cdap.proto.Id;
import co.cask.cdap.templates.AdapterSpecification;
import com.google.common.io.Closeables;
import com.google.common.io.InputSupplier;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.twill.filesystem.Location;

import java.io.Reader;
import java.util.concurrent.TimeUnit;

/**
 * LocalArchiveLoaderStage gets a {@link Location} and emits a {@link ApplicationDeployable}.
 * <p>
 * This stage is responsible for reading the JAR and generating an ApplicationSpecification
 * that is forwarded to the next stage of processing.
 * </p>
 */
public class ConfigureAdapterStage extends AbstractStage<AdapterDeploymentInfo> {
  private static final Gson GSON = ApplicationSpecificationAdapter.addTypeAdapters(new GsonBuilder()).create();
  private final Id.Namespace namespace;
  private final String adapterName;
  private final Location templateJarLocation;

  /**
   * Constructor with hit for handling type.
   */
  public ConfigureAdapterStage(Id.Namespace namespace, String adapterName, Location templateJarLocation) {
    super(TypeToken.of(AdapterDeploymentInfo.class));
    this.namespace = namespace;
    this.adapterName = adapterName;
    this.templateJarLocation = templateJarLocation;
  }

  /**
   * Creates a {@link InMemoryConfigurator} to run through
   * the process of generation of {@link AdapterSpecification}
   *
   * @param deploymentInfo Location of the input and output location
   */
  @Override
  public void process(AdapterDeploymentInfo deploymentInfo) throws Exception {
    InMemoryAdapterConfigurator inMemoryAdapterConfigurator =
      new InMemoryAdapterConfigurator(namespace, templateJarLocation, adapterName,
                                      deploymentInfo.getAdapterConfig(), deploymentInfo.getTemplateSpec());
    ConfigResponse configResponse = inMemoryAdapterConfigurator.config().get(120, TimeUnit.SECONDS);
    InputSupplier<? extends Reader> configSupplier = configResponse.get();
    if (configResponse.getExitCode() != 0 || configSupplier == null) {
      throw new IllegalArgumentException("Failed to configure adapter: " + deploymentInfo);
    }
    Reader reader = configSupplier.getInput();
    try {
      emit(GSON.fromJson(reader, AdapterSpecification.class));
    } finally {
      Closeables.closeQuietly(reader);
    }
  }
}
