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

package co.cask.cdap.templates.etl.batch.sources;

import co.cask.cdap.api.dataset.lib.FileSet;
import co.cask.cdap.api.dataset.lib.FileSetArguments;
import co.cask.cdap.templates.etl.api.Property;
import co.cask.cdap.templates.etl.api.StageConfigurer;
import co.cask.cdap.templates.etl.api.batch.BatchSource;
import co.cask.cdap.templates.etl.api.batch.BatchSourceContext;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * CDAP Table Dataset Batch Source.
 */
public class FileSetSource extends BatchSource<byte[], byte[]> {
  private static final String TABLE_NAME = "name";
  private static final String INPUT_PATH = "inputPath";
  @Override
  public void configure(StageConfigurer configurer) {
    configurer.setName(FileSetSource.class.getSimpleName());
    configurer.setDescription("CDAP FileSet Dataset Batch Source");
    configurer.addProperty(new Property(TABLE_NAME, "Fileset Name", true));
    configurer.addProperty(new Property(INPUT_PATH, "Output path to write to", true));
  }

  @Override
  public void prepareJob(BatchSourceContext context) {
    Map<String, String> inputArgs = Maps.newHashMap();
    FileSetArguments.setInputPath(inputArgs, context.getRuntimeArguments().get(INPUT_PATH));
    FileSet input = context.getDataset(context.getRuntimeArguments().get(TABLE_NAME), inputArgs);
    context.setInput(context.getRuntimeArguments().get(TABLE_NAME), input);
  }
}
