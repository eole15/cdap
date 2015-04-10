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

import co.cask.cdap.api.dataset.lib.FileSet;
import co.cask.cdap.api.dataset.lib.FileSetArguments;
import co.cask.cdap.api.dataset.lib.FileSetProperties;
import co.cask.cdap.api.templates.ApplicationTemplate;
import co.cask.cdap.templates.etl.batch.sinks.FileSetSink;
import co.cask.cdap.templates.etl.batch.sources.FileSetSource;
import co.cask.cdap.templates.etl.common.config.ETLStage;
import co.cask.cdap.test.ApplicationManager;
import co.cask.cdap.test.DataSetManager;
import co.cask.cdap.test.MapReduceManager;
import co.cask.cdap.test.TestBase;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.twill.filesystem.Location;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by rsinha on 4/7/15.
 */
public class FileSetSourceSinkTest extends TestBase {
  private static final String sourceFileset = "inputFileset";
  private static final String sinkFileset = "outputFileset";

  @BeforeClass
  public static void beforeClass() throws Exception {
    addDatasetInstance("fileSet", sourceFileset, FileSetProperties.builder()
      .setBasePath(sourceFileset)
      .setInputFormat(TextInputFormat.class)
      .build());

    addDatasetInstance("fileSet", sinkFileset, FileSetProperties.builder()
      .setBasePath(sinkFileset)
      .setOutputFormat(TextOutputFormat.class)
      .build());
  }

  @AfterClass
  public static void afterClass() throws Exception {
    clear();
  }

  @Test
  public void testConfig() throws Exception {

    Map<String, String> fileset1FileArgs = Maps.newHashMap();
    FileSetArguments.setInputPath(fileset1FileArgs, "file1");
    DataSetManager<FileSet> table1 = getDataset(sourceFileset, fileset1FileArgs);
    FileSet inputFileset = table1.get();


    ApplicationManager batchManager = deployApplication(ETLBatchTemplate.class);



    OutputStream out = inputFileset.getInputLocations().get(0).getOutputStream();
    out.write(42);
    out.close();

    ApplicationTemplate<ETLBatchConfig> appTemplate = new ETLBatchTemplate();
    ETLBatchConfig adapterConfig = constructETLBatchConfig();
    MockAdapterConfigurer adapterConfigurer = new MockAdapterConfigurer();
    appTemplate.configureAdapter("myAdapter", adapterConfig, adapterConfigurer);
    Map<String, String> mapReduceArgs = Maps.newHashMap();
    for (Map.Entry<String, String> entry : adapterConfigurer.getArguments().entrySet()) {
      mapReduceArgs.put(entry.getKey(), entry.getValue());
    }
    MapReduceManager mrManager = batchManager.startMapReduce("ETLMapReduce", mapReduceArgs);
    mrManager.waitForFinish(5, TimeUnit.MINUTES);
    batchManager.
    batchManager.stopAll();

    DataSetManager<FileSet> table2 = getDataset(sinkFileset);
    FileSet outputFileset = table2.get();
    Assert.assertNotNull(outputFileset.getOutputLocation());
  }


  private ETLBatchConfig constructETLBatchConfig() {
    ETLStage source = new ETLStage(FileSetSource.class.getSimpleName(), ImmutableMap.of("name", sourceFileset,
                                                                                        "inputPath", "file1"));
    ETLStage sink = new ETLStage(FileSetSink.class.getSimpleName(), ImmutableMap.of("name", sinkFileset,
                                                                                    "outputPath", "abc"));
    List<ETLStage> transformList = Lists.newArrayList();
    return new ETLBatchConfig("", source, sink, transformList);
  }
}
