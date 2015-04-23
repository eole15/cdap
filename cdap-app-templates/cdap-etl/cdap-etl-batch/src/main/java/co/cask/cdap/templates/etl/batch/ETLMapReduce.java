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

import co.cask.cdap.api.ProgramLifecycle;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.mapreduce.AbstractMapReduce;
import co.cask.cdap.api.mapreduce.MapReduceContext;
import co.cask.cdap.api.metrics.Metrics;
import co.cask.cdap.templates.etl.api.Transform;
import co.cask.cdap.templates.etl.api.TransformStage;
import co.cask.cdap.templates.etl.api.batch.BatchSink;
import co.cask.cdap.templates.etl.api.batch.BatchSinkContext;
import co.cask.cdap.templates.etl.api.batch.BatchSource;
import co.cask.cdap.templates.etl.api.batch.BatchSourceContext;
import co.cask.cdap.templates.etl.api.config.ETLStage;
import co.cask.cdap.templates.etl.batch.config.ETLBatchConfig;
import co.cask.cdap.templates.etl.common.Constants;
import co.cask.cdap.templates.etl.common.StageMetrics;
import co.cask.cdap.templates.etl.common.TransformExecutor;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * MapReduce driver for Batch ETL Adapters.
 */
public class ETLMapReduce extends AbstractMapReduce {
  private static final Logger LOG = LoggerFactory.getLogger(ETLMapReduce.class);
  private static final Gson GSON = new Gson();
  private Metrics mrMetrics;

  @Override
  public void configure() {
    setName(ETLMapReduce.class.getSimpleName());
    setDescription("MapReduce driver for Batch ETL Adapters");
  }

  @Override
  public void beforeSubmit(MapReduceContext context) throws Exception {
    Job job = context.getHadoopJob();
    Map<String, String> runtimeArgs = context.getRuntimeArguments();

    Preconditions.checkArgument(runtimeArgs.containsKey(Constants.ADAPTER_NAME));
    Preconditions.checkArgument(runtimeArgs.containsKey(Constants.CONFIG_KEY));
    Preconditions.checkArgument(runtimeArgs.containsKey(Constants.Source.PLUGINID));
    Preconditions.checkArgument(runtimeArgs.containsKey(Constants.Sink.PLUGINID));
    Preconditions.checkArgument(runtimeArgs.containsKey(Constants.Transform.PLUGINIDS));

    ETLBatchConfig config = GSON.fromJson(runtimeArgs.get(Constants.CONFIG_KEY), ETLBatchConfig.class);

    prepareSource(context, config.getSource());
    prepareSink(context, config.getSink());

    if (config.getResources() != null) {
      context.setMapperResources(config.getResources());
    }
    job.setMapperClass(ETLMapper.class);
    job.setNumReduceTasks(0);
  }

  private void prepareSource(MapReduceContext context, ETLStage sourceStage) throws Exception {
    String sourcePluginId = context.getRuntimeArguments().get(Constants.Source.PLUGINID);
    BatchSource source = context.newPluginInstance(sourcePluginId);
    BatchSourceContext sourceContext = new MapReduceSourceContext(context, mrMetrics, sourcePluginId);
    LOG.info("Source Stage : {}", sourceStage);
    LOG.info("Source Class : {}", source.getClass().getName());
    source.prepareJob(sourceContext);
  }

  private void prepareSink(MapReduceContext context, ETLStage sinkStage) throws Exception {
    String sinkPluginId = context.getRuntimeArguments().get(Constants.Sink.PLUGINID);
    BatchSink sink = context.newPluginInstance(sinkPluginId);
    BatchSinkContext sinkContext = new MapReduceSinkContext(context, mrMetrics, sinkPluginId);
    LOG.info("Sink Stage : {}", sinkStage);
    LOG.info("Sink Class : {}", sink.getClass().getName());
    sink.prepareJob(sinkContext);
  }

  @Override
  public void onFinish(boolean succeeded, MapReduceContext context) {
    LOG.info("Batch Run for Adapter {} : {}", context.getRuntimeArguments().get(Constants.ADAPTER_NAME), succeeded);
  }

  /**
   * Mapper Driver for ETL Transforms.
   */
  public static class ETLMapper extends Mapper implements ProgramLifecycle<MapReduceContext> {
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() { }.getType();

    private TransformExecutor<KeyValue, KeyValue> transformExecutor;
    private Metrics mapperMetrics;

    @Override
    public void initialize(MapReduceContext context) throws Exception {
      Map<String, String> runtimeArgs = context.getRuntimeArguments();
      ETLBatchConfig etlConfig = GSON.fromJson(runtimeArgs.get(Constants.CONFIG_KEY), ETLBatchConfig.class);
      String sourceId = runtimeArgs.get(Constants.Source.PLUGINID);
      String sinkId = runtimeArgs.get(Constants.Sink.PLUGINID);
      List<String> transformIds = GSON.fromJson(runtimeArgs.get(Constants.Transform.PLUGINIDS), STRING_LIST_TYPE);


      List<ETLStage> stageList = etlConfig.getTransforms();
      LOG.info("Transform Stages : {}", stageList);

      List<Transform> pipeline = Lists.newArrayListWithCapacity(stageList.size() + 2);
      List<StageMetrics> stageMetrics = Lists.newArrayListWithCapacity(stageList.size() + 2);

      BatchSource source = context.newPluginInstance(sourceId);
      source.initialize(etlConfig.getSource());
      pipeline.add(source);
      stageMetrics.add(new StageMetrics(mapperMetrics, StageMetrics.Type.SOURCE, etlConfig.getSource().getName()));

      addTransforms(stageList, pipeline, stageMetrics, transformIds, context);

      BatchSink sink = context.newPluginInstance(sinkId);
      sink.initialize(etlConfig.getSink());
      pipeline.add(sink);
      stageMetrics.add(new StageMetrics(mapperMetrics, StageMetrics.Type.SINK, etlConfig.getSink().getName()));

      transformExecutor = new TransformExecutor<KeyValue, KeyValue>(pipeline, stageMetrics);
    }

    private void addTransforms(List<ETLStage> stageConfigs, List<Transform> pipeline,
                               List<StageMetrics> stageMetrics, List<String> transformIds,
                               MapReduceContext context) throws InstantiationException {
      Preconditions.checkArgument(stageConfigs.size() == transformIds.size());

      for (int i = 0; i < stageConfigs.size(); i++) {
        ETLStage stageConfig = stageConfigs.get(i);
        String transformId = transformIds.get(i);
        TransformStage transform = context.newPluginInstance(transformId);
        BatchStageContext transformContext = new BatchStageContext(context, mapperMetrics, transformId);
        transform.initialize(transformContext);

        pipeline.add(transform);
        stageMetrics.add(new StageMetrics(mapperMetrics, StageMetrics.Type.TRANSFORM, stageConfig.getName()));
      }
    }

    @Override
    public void map(Object key, Object value, Context context) throws IOException, InterruptedException {
      try {
        KeyValue input = new KeyValue(key, value);
        for (KeyValue output : transformExecutor.runOneIteration(input)) {
          context.write(output.getKey(), output.getValue());
        }
      } catch (Exception e) {
        LOG.error("Exception thrown in BatchDriver Mapper : {}", e);
        Throwables.propagate(e);
      }
    }

    @Override
    public void destroy() {
      // no-op
    }
  }
}
