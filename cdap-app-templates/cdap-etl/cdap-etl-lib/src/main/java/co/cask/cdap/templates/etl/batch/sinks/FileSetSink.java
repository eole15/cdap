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

package co.cask.cdap.templates.etl.batch.sinks;

import co.cask.cdap.api.dataset.lib.FileSet;
import co.cask.cdap.api.dataset.lib.FileSetArguments;
import co.cask.cdap.templates.etl.api.Property;
import co.cask.cdap.templates.etl.api.StageConfigurer;
import co.cask.cdap.templates.etl.api.batch.BatchSink;
import co.cask.cdap.templates.etl.api.batch.BatchSinkContext;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * CDAP Table Dataset Batch Sink.
 */
public class FileSetSink extends BatchSink<byte[], byte[]> {
  private static final String TABLE_NAME = "name";
  private static final String OUTPUT_PATH = "outputPath";

  @Override
  public void configure(StageConfigurer configurer) {
    configurer.setName("FileSetSink");
    configurer.setDescription("CDAP Key Value FileSet Dataset Batch Sink");
    configurer.addProperty(new Property(TABLE_NAME, "Dataset Name", true));
    configurer.addProperty(new Property(OUTPUT_PATH, "Output path to write to", true));
  }

  @Override
  public void prepareJob(BatchSinkContext context) {
    Map<String, String> outputArgs = Maps.newHashMap();
    FileSetArguments.setOutputPath(outputArgs, context.getRuntimeArguments().get(OUTPUT_PATH)
      .concat("_" + String.valueOf(context.getLogicalStartTime())));
    FileSet output = context.getDataset(context.getRuntimeArguments().get(TABLE_NAME), outputArgs);
    context.setOutput(context.getRuntimeArguments().get(TABLE_NAME), output);
  }
}
