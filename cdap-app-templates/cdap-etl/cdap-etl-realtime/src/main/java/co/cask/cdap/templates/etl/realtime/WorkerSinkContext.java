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

package co.cask.cdap.templates.etl.realtime;

import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.api.data.DatasetInstantiationException;
import co.cask.cdap.api.data.stream.StreamBatchWriter;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.stream.StreamEventData;
import co.cask.cdap.api.worker.Worker;
import co.cask.cdap.api.worker.WorkerContext;
import co.cask.cdap.templates.etl.api.StageSpecification;
import co.cask.cdap.templates.etl.api.config.ETLStage;
import co.cask.cdap.templates.etl.api.realtime.SinkContext;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Implementation of {@link SinkContext} for a {@link Worker} driver.
 */
public class WorkerSinkContext implements SinkContext {
  private WorkerContext context;
  private final StageSpecification specification;
  private final ETLStage stage;
  private final DatasetContext datasetContext;

  public WorkerSinkContext(WorkerContext context, ETLStage sinkStage, StageSpecification spec,
                           DatasetContext datasetContext) {
    this.context = context;
    this.specification = spec;
    this.stage = sinkStage;
    this.datasetContext = datasetContext;
  }

  @Override
  public StageSpecification getSpecification() {
    return specification;
  }

  @Override
  public int getInstanceId() {
    return context.getInstanceId();
  }

  @Override
  public int getInstanceCount() {
    return context.getInstanceCount();
  }

  @Override
  public <T extends Dataset> T getDataset(String name) throws DatasetInstantiationException {
    return datasetContext.getDataset(name);
  }

  @Override
  public <T extends Dataset> T getDataset(String name, Map<String, String> arguments)
    throws DatasetInstantiationException {
    return datasetContext.getDataset(name, arguments);
  }

  @Override
  public Map<String, String> getRuntimeArguments() {
    return stage.getProperties();
  }

  @Override
  public void write(String stream, String data) throws IOException {
    context.write(stream, data);
  }

  @Override
  public void write(String stream, String data, Map<String, String> headers) throws IOException {
    context.write(stream, data, headers);
  }

  @Override
  public void write(String stream, ByteBuffer data) throws IOException {
    context.write(stream, data);
  }

  @Override
  public void write(String stream, StreamEventData data) throws IOException {
    context.write(stream, data);
  }

  @Override
  public void writeFile(String stream, File file, String contentType) throws IOException {
    context.writeFile(stream, file, contentType);
  }

  @Override
  public StreamBatchWriter createBatchWriter(String stream, String contentType) throws IOException {
    return context.createBatchWriter(stream, contentType);
  }
}
