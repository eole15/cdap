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

package co.cask.cdap.templates.etl.realtime.sources;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.templates.etl.api.Emitter;
import co.cask.cdap.templates.etl.api.Property;
import co.cask.cdap.templates.etl.api.StageConfigurer;
import co.cask.cdap.templates.etl.api.realtime.RealtimeContext;
import co.cask.cdap.templates.etl.api.realtime.RealtimeSource;
import co.cask.cdap.templates.etl.api.realtime.SourceState;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Realtime TestSource that emits {@link StructuredRecord} objects as needed for testing.
 */
public class TestSource extends RealtimeSource<StructuredRecord> {
  private static final Logger LOG = LoggerFactory.getLogger(TestSource.class);
  private static final String COUNT = "count";
  public static final String PROPERTY_TYPE = "type";
  public static final String STREAM_TYPE = "stream";
  public static final String TABLE_TYPE = "table";

  private String type;

  @Override
  public void configure(StageConfigurer configurer) {
    configurer.setDescription("Source that can generate test data for Real-time Stream and Table Sinks");
    configurer.addProperty(new Property(PROPERTY_TYPE, "The type of data to be generated. Currently, only two types" +
      " - 'stream' and 'table' are supported. By default, it generates a structured record containing one field - " +
      "'data' of type String with value 'Hello'", false));
  }

  @Override
  public void initialize(RealtimeContext context) throws Exception {
    super.initialize(context);
    type = context.getRuntimeArguments().get("type");
  }

  @Nullable
  @Override
  public SourceState poll(Emitter<StructuredRecord> writer, SourceState currentState) {
    try {
      TimeUnit.MILLISECONDS.sleep(100);
    } catch (InterruptedException e) {
      LOG.error("Some Error in Source");
    }

    int prevCount;
    if (currentState.getState(COUNT) != null) {
      prevCount = Bytes.toInt(currentState.getState(COUNT));
      prevCount++;
      currentState.setState(COUNT, Bytes.toBytes(prevCount));
    } else {
      prevCount = 1;
      currentState = new SourceState();
      currentState.setState(COUNT, Bytes.toBytes(prevCount));
    }

    LOG.info("Emitting data! {}", prevCount);
    if (type == null) {
      writeDefaultRecords(writer);
    } else if (STREAM_TYPE.equals(type)) {
      writeRecordsForStreamConsumption(writer);
    } else if (TABLE_TYPE.equals(type)) {
      writeRecordsForTableConsumption(writer);
    }
    return currentState;
  }

  private void writeDefaultRecords(Emitter<StructuredRecord> writer) {
    Schema.Field dataField = Schema.Field.of("data", Schema.of(Schema.Type.STRING));
    StructuredRecord.Builder recordBuilder = StructuredRecord.builder(Schema.recordOf("defaultRecord", dataField));
    recordBuilder.set("data", "Hello");
    writer.emit(recordBuilder.build());
  }

  private void writeRecordsForStreamConsumption(Emitter<StructuredRecord> writer) {
    Schema.Field dataField = Schema.Field.of("data", Schema.of(Schema.Type.STRING));
    Schema.Field headersField = Schema.Field.of("headers", Schema.mapOf(Schema.of(Schema.Type.STRING),
                                                                        Schema.of(Schema.Type.STRING)));
    // emit only string
    StructuredRecord.Builder recordBuilder = StructuredRecord.builder(Schema.recordOf("StringRecord", dataField));
    recordBuilder.set("data", "Hello");
    writer.emit(recordBuilder.build());
    // emit string + headers
    recordBuilder = StructuredRecord.builder(Schema.recordOf("StringHeadersRecord", dataField, headersField));
    recordBuilder.set("data", "Hello");
    recordBuilder.set("headers", ImmutableMap.of("h1", "v1"));
    writer.emit(recordBuilder.build());
    // byte array + headers
    dataField = Schema.Field.of("data", Schema.of(Schema.Type.BYTES));
    recordBuilder = StructuredRecord.builder(Schema.recordOf("ByteArrayHeadersRecord", dataField, headersField));
    recordBuilder.set("data", "Hello".getBytes(Charsets.UTF_8));
    recordBuilder.set("headers", ImmutableMap.of("h1", "v1"));
    writer.emit(recordBuilder.build());
    // ByteBuffer + headers
    recordBuilder = StructuredRecord.builder(Schema.recordOf("ByteBufferHeadersRecord", dataField, headersField));
    recordBuilder.set("data", ByteBuffer.wrap("Hello".getBytes(Charsets.UTF_8)));
    recordBuilder.set("headers", ImmutableMap.of("h1", "v1"));
    writer.emit(recordBuilder.build());
  }

  private void writeRecordsForTableConsumption(Emitter<StructuredRecord> writer) {
    Schema.Field idField = Schema.Field.of("id", Schema.of(Schema.Type.INT));
    Schema.Field nameField = Schema.Field.of("name", Schema.of(Schema.Type.STRING));
    Schema.Field scoreField = Schema.Field.of("score", Schema.of(Schema.Type.DOUBLE));
    Schema.Field graduatedField = Schema.Field.of("graduated", Schema.of(Schema.Type.BOOLEAN));
    Schema.Field binaryNameField = Schema.Field.of("binary", Schema.of(Schema.Type.BYTES));
    Schema.Field timeField = Schema.Field.of("time", Schema.of(Schema.Type.LONG));
    StructuredRecord.Builder recordBuilder = StructuredRecord.builder(
      Schema.recordOf("tableRecord", idField, nameField, scoreField, graduatedField, binaryNameField, timeField));
    recordBuilder
      .set("id", 1)
      .set("name", "Bob").
      set("score", 3.4)
      .set("graduated", false)
      .set("binary", "Bob".getBytes(Charsets.UTF_8))
      .set("time", System.currentTimeMillis());
    writer.emit(recordBuilder.build());
  }
}
