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

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.templates.plugins.PluginConfig;
import co.cask.cdap.templates.etl.api.Emitter;
import co.cask.cdap.templates.etl.api.config.ETLStage;
import co.cask.cdap.templates.etl.common.Properties;
import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;
import javax.annotation.Nullable;

/**
 * CDAP Table Dataset Batch Sink.
 */
@Plugin(type = "sink")
@Name("KVTableSink")
@Description("CDAP Key Value Table Dataset Batch Sink")
public class KVTableSink extends BatchWritableSink<StructuredRecord, byte[], byte[]> {

  private static final String NAME_DESC = "Dataset Name";
  private static final String KEY_FIELD_DESC = "The name of the field to use as the key. Its type must be bytes. " +
    "Defaults to 'key'.";
  private static final String VALUE_FIELD_DESC = "The name of the field to use as the value. Its type must be bytes. " +
    "Defaults to 'value'.";

  /**
   * Config class for KVTableSink
   */
  public static class KVTableConfig extends PluginConfig {
    @Description(NAME_DESC)
    private String name;

    @Name(Properties.KeyValueTable.KEY_FIELD)
    @Description(KEY_FIELD_DESC)
    @Nullable
    private String keyField;

    @Name(Properties.KeyValueTable.VALUE_FIELD)
    @Description(VALUE_FIELD_DESC)
    @Nullable
    private String valueField;

    public KVTableConfig(String name, String keyField, String valueField) {
      this.name = name;
      this.keyField = keyField;
      this.valueField = valueField;
    }
  }

  private final KVTableConfig kvTableConfig;

  public KVTableSink(KVTableConfig kvTableConfig) {
    this.kvTableConfig = kvTableConfig;
  }

  @Override
  protected String getDatasetType(ETLStage config) {
    return KeyValueTable.class.getName();
  }

  @Override
  public void transform(StructuredRecord input, Emitter<KeyValue<byte[], byte[]>> emitter) throws Exception {
    Object key = input.get(kvTableConfig.keyField);
    Preconditions.checkArgument(key != null, "Key cannot be null.");
    byte[] keyBytes = key instanceof ByteBuffer ? Bytes.toBytes((ByteBuffer) key) : (byte[]) key;
    Object val = input.get(kvTableConfig.valueField);
    byte[] valBytes = val instanceof ByteBuffer ? Bytes.toBytes((ByteBuffer) val) : (byte[]) val;
    emitter.emit(new KeyValue<byte[], byte[]>(keyBytes, valBytes));
  }
}
