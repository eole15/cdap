/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.hive.stream;

import co.cask.cdap.api.data.format.FormatSpecification;
import co.cask.cdap.api.data.schema.UnsupportedTypeException;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.data.format.ByteBufferRecordFormat;
import co.cask.cdap.data.format.RecordFormats;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.hive.context.ContextManager;
import co.cask.cdap.hive.objectinspector.ObjectInspectorFactory;
import co.cask.cdap.hive.serde.ObjectTranslator;
import com.google.common.collect.Lists;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.apache.hadoop.hive.serde2.SerDe;
import org.apache.hadoop.hive.serde2.SerDeException;
import org.apache.hadoop.hive.serde2.SerDeStats;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hadoop.io.ObjectWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * SerDe to deserialize Stream Events. It MUST implement the deprecated SerDe interface instead of extending the
 * abstract SerDe class, otherwise we get ClassNotFound exceptions on cdh4.x.
 */
public class StreamSerDe implements SerDe {
  private static final Logger LOG = LoggerFactory.getLogger(StreamSerDe.class);
  private static final int BODY_OFFSET = 2;
  private ArrayList<String> columnNames;
  private ArrayList<TypeInfo> columnTypes;
  private List<String> bodyColumnNames;
  private List<TypeInfo> bodyColumnTypes;
  private ObjectInspector inspector;
  private ByteBufferRecordFormat streamFormat;

  // initialize gets called multiple times by Hive. It may seem like a good idea to put additional settings into
  // the conf, but be very careful when doing so. If there are multiple hive tables involved in a query, initialize
  // for each table is called before input splits are fetched for any table. It is therefore not safe to put anything
  // the input format may need into conf in this method. Rather, use StorageHandler's method to place needed config
  // into the properties map there, which will get passed here and also copied into the job conf for the input
  // format to consume.
  @Override
  public void initialize(Configuration conf, Properties properties) throws SerDeException {
    // The columns property comes from the Hive metastore, which has it from the create table statement
    // It is then important that this schema be accurate and in the right order - the same order as
    // object inspectors will reflect them.
    columnNames = Lists.newArrayList(properties.getProperty(serdeConstants.LIST_COLUMNS).split(","));
    columnTypes = TypeInfoUtils.getTypeInfosFromTypeString(properties.getProperty(serdeConstants.LIST_COLUMN_TYPES));
    // timestamp and headers are guaranteed to be the first columns in a stream table.
    // the rest of the columns are for the stream body.
    bodyColumnNames = columnNames.subList(BODY_OFFSET, columnNames.size());
    bodyColumnTypes = columnTypes.subList(BODY_OFFSET, columnTypes.size());

    int numCols = columnNames.size();

    final List<ObjectInspector> columnOIs = new ArrayList<ObjectInspector>(numCols);

    for (int i = 0; i < numCols; i++) {
      columnOIs.add(TypeInfoUtils.getStandardJavaObjectInspectorFromTypeInfo(columnTypes.get(i)));
    }

    // this is the object inspector hive will use on the result of deserialize
    this.inspector = ObjectInspectorFactory.getStandardStructObjectInspector(columnNames, columnOIs);

    String streamName = properties.getProperty(Constants.Explore.STREAM_NAME);
    try {
      // Get the stream format from the stream config.
      ContextManager.Context context = ContextManager.getContext(conf);
      // get the stream admin from the context, which will let us get stream information such as the path
      StreamAdmin streamAdmin = context.getStreamAdmin();
      StreamConfig streamConfig = streamAdmin.getConfig(streamName);
      FormatSpecification formatSpec = streamConfig.getFormat();
      this.streamFormat = (ByteBufferRecordFormat) RecordFormats.createInitializedFormat(formatSpec);
    } catch (UnsupportedTypeException e) {
      // this should have been validated up front when schema was set on the stream.
      // if we hit this something went wrong much earlier.
      LOG.error("Schema unsupported by format.", e);
      throw new SerDeException("Schema unsupported by format.", e);
    } catch (IOException e) {
      LOG.error("Could not get the config for stream {}.", streamName, e);
      throw new SerDeException("Could not get the config for stream " + streamName, e);
    } catch (Exception e) {
      LOG.error("Could not create the format for stream {}.", streamName, e);
      throw new SerDeException("Could not create the format for stream " + streamName, e);
    }
  }

  @Override
  public Class<? extends Writable> getSerializedClass() {
    return Text.class;
  }

  @Override
  public Writable serialize(Object o, ObjectInspector objectInspector) throws SerDeException {
    // should not be writing to streams through this
    throw new SerDeException("Stream serialization through Hive is not supported.");
  }

  @Override
  public SerDeStats getSerDeStats() {
    return new SerDeStats();
  }

  @Override
  public Object deserialize(Writable writable) throws SerDeException {
    // this should always contain a StreamEvent object
    ObjectWritable objectWritable = (ObjectWritable) writable;
    StreamEvent streamEvent = (StreamEvent) objectWritable.get();

    // timestamp and headers are always guaranteed to be first.
    List<Object> event = Lists.newArrayList();
    event.add(streamEvent.getTimestamp());
    event.add(streamEvent.getHeaders());

    try {
      // The format should always format the body into a record.
      event.addAll(ObjectTranslator.flattenRecord(
        streamFormat.read(streamEvent.getBody()), bodyColumnNames, bodyColumnTypes));
      return event;
    } catch (Throwable t) {
      LOG.info("Unable to format the stream body.", t);
      throw new SerDeException("Unable to format the stream body.", t);
    }
  }

  @Override
  public ObjectInspector getObjectInspector() throws SerDeException {
    return inspector;
  }
}