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

package co.cask.cdap.templates.etl.batch;

import co.cask.cdap.api.data.DatasetInstantiationException;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.mapreduce.MapReduceContext;
import co.cask.cdap.api.metrics.Metrics;
import co.cask.cdap.templates.etl.api.StageSpecification;
import co.cask.cdap.templates.etl.api.batch.BatchContext;
import co.cask.cdap.templates.etl.api.config.ETLStage;

import java.util.Collections;
import java.util.Map;

/**
 * Abstract implementation of {@link BatchContext} using {@link MapReduceContext}.
 */
public abstract class MapReduceBatchContext implements BatchContext {
  private final StageSpecification specification;
  private final ETLStage stage;
  private final Metrics metrics;

  protected final MapReduceContext mrContext;

  public MapReduceBatchContext(MapReduceContext context, ETLStage stage, StageSpecification specification,
                               Metrics metrics) {
    this.mrContext = context;
    this.specification = specification;
    this.stage = stage;
    this.metrics = metrics;
  }

  @Override
  public StageSpecification getSpecification() {
    return specification;
  }

  @Override
  public long getLogicalStartTime() {
    return mrContext.getLogicalStartTime();
  }

  @Override
  public <T> T getHadoopJob() {
    return mrContext.getHadoopJob();
  }

  @Override
  public <T extends Dataset> T getDataset(String name) throws DatasetInstantiationException {
    return mrContext.getDataset(name);
  }

  @Override
  public <T extends Dataset> T getDataset(String name, Map<String, String> arguments)
    throws DatasetInstantiationException {
    return mrContext.getDataset(name, arguments);
  }

  @Override
  public Map<String, String> getRuntimeArguments() {
    return Collections.unmodifiableMap(stage.getProperties());
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }
}
