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

import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.dataset.lib.cube.Cube;
import co.cask.cdap.api.dataset.lib.cube.CubeFact;
import co.cask.cdap.templates.etl.api.Emitter;
import co.cask.cdap.templates.etl.api.Property;
import co.cask.cdap.templates.etl.api.StageConfigurer;
import co.cask.cdap.templates.etl.api.config.ETLStage;
import co.cask.cdap.templates.etl.common.StructuredRecordToCubeFact;

/**
 * A {@link co.cask.cdap.templates.etl.api.batch.BatchSink} that writes data to a {@link Cube} dataset.
 * <p/>
 * This {@link BatchCubeSink} takes {@link StructuredRecord} in, maps it to a {@link CubeFact} using mapping
 * configuration provided with {@link #MAPPING_CONFIG_PROPERTY} property, and writes it to a {@link Cube} dataset
 * identified by {@link #NAME} property.
 * <p/>
 * If {@link Cube} dataset does not exist, it will be created using properties provided with this sink. See more
 * information on available {@link Cube} dataset configuration properties at
 * {@link co.cask.cdap.data2.dataset2.lib.cube.CubeDatasetDefinition}.
 * <p/>
 * To configure transformation from {@link StructuredRecord} to a {@link CubeFact} the
 * mapping configuration is required, as per {@link StructuredRecordToCubeFact} documentation.
 */
// todo: add unit-test once CDAP-2156 is resolved
public class BatchCubeSink extends BatchWritableSink<StructuredRecord, byte[], CubeFact> {
  public static final String MAPPING_CONFIG_PROPERTY = "mapping.config";

  private StructuredRecordToCubeFact transform;

  @Override
  public void configure(StageConfigurer configurer) {
    configurer.setName("BatchCubeSink");
    configurer.setDescription("CDAP Cube Dataset Batch Sink");
    configurer.addProperty(new Property(NAME, "Name of the Cube dataset. If the Cube does not already exist," +
      " one will be created.", true));
    configurer.addProperty(new Property(Cube.PROPERTY_RESOLUTIONS,
                                        "Aggregation resolutions. See Cube dataset " +
                                          "configuration details for more information", true));
    configurer.addProperties(StructuredRecordToCubeFact.getProperties());
  }

  @Override
  public void initialize(ETLStage stageConfig) throws Exception {
    super.initialize(stageConfig);
    transform = new StructuredRecordToCubeFact(stageConfig.getProperties());
  }

  @Override
  protected String getDatasetType(ETLStage config) {
    return Cube.class.getName();
  }

  @Override
  public void transform(StructuredRecord input, Emitter<KeyValue<byte[], CubeFact>> emitter) throws Exception {
    emitter.emit(new KeyValue<byte[], CubeFact>(null, transform.transform(input)));
  }
}
