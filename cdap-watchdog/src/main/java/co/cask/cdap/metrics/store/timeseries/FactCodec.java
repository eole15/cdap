/*
 * Copyright 2015 Cask Data, Inc.
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

package co.cask.cdap.metrics.store.timeseries;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.metrics.data.EntityTable;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper for serde of Fact into columnar format.
 */
public class FactCodec {
  private static final Logger LOG = LoggerFactory.getLogger(FactCodec.class);
  // encoding types
  private static final String TYPE_MEASURE_NAME = "measureName";
  private static final String TYPE_TAGS_GROUP = "tagsGroup";

  private final EntityTable entityTable;

  private final int resolution;
  private final int rollTimebaseInterval;
  // Cache for delta values.
  private final byte[][] deltaCache;

  public FactCodec(EntityTable entityTable, int resolution, int rollTimebaseInterval) {
    this.entityTable = entityTable;
    this.resolution = resolution;
    this.rollTimebaseInterval = rollTimebaseInterval;
    this.deltaCache = createDeltaCache(rollTimebaseInterval);
  }

  /**
   * Builds row key for write and get operations.
   * @param tagValues tags
   * @param measureName measure name
   * @param ts timestamp
   * @return row key
   */
  public byte[] createRowKey(List<TagValue> tagValues, String measureName, long ts) {
    // "false" would write null in tag values as "undefined"
    return createRowKey(tagValues, measureName, ts, false, false);
  }

  /**
   * Builds start row key for scan operation.
   * @param tagValues tags
   * @param measureName measure name
   * @param ts timestamp
   * @return row key
   */
  public byte[] createStartRowKey(List<TagValue> tagValues, String measureName, long ts, boolean isSearch) {
    // "false" would write null in tag values as "undefined"
    return createRowKey(tagValues, measureName, ts, false, isSearch);
  }

  /**
   * Builds end row key for scan operation.
   * @param tagValues tags
   * @param measureName measure name
   * @param ts timestamp
   * @return row key
   */
  public byte[] createEndRowKey(List<TagValue> tagValues, String measureName, long ts, boolean isSearch) {
    // "false" would write null in tag values as "undefined"
    return createRowKey(tagValues, measureName, ts, true, isSearch);
  }

  private byte[] createRowKey(List<TagValue> tagValues, String measureName, long ts, boolean stopKey,
                              boolean isSearch) {
    // Row key format: <encoded agg group><time base><encoded tag1 value>...<encoded tagN value><encoded measure name>.
    // todo: reserve first byte for versioning and other things for future
    // "+2" is for <encoded agg group> and <encoded measure name>
    byte[] rowKey = new byte[(tagValues.size() + 2) * entityTable.getIdSize() + Bytes.SIZEOF_INT];
    int offset;

    if (isSearch) {
      offset = writeAnyEncoded(rowKey, 0, stopKey);
    } else {
      offset = writeEncodedAggGroup(tagValues, rowKey, 0);
    }

    long timestamp = roundToResolution(ts);
    int timeBase = getTimeBase(timestamp);
    offset = Bytes.putInt(rowKey, offset, timeBase);

    for (TagValue tagValue : tagValues) {
      if (tagValue.getValue() != null) {
        // encoded value is unique within values of the tag name
        offset = writeEncoded(tagValue.getTagName(), tagValue.getValue(), rowKey, offset);
      } else {
        // todo: this is only applicable for constructing scan, throw smth if constructing key for writing data
        // writing "ANY" as a value
        offset = writeAnyEncoded(rowKey, offset, stopKey);
      }
    }

    if (measureName != null) {
      writeEncoded(TYPE_MEASURE_NAME, measureName, rowKey, offset);
    } else {
      // todo: this is only applicable for constructing scan, throw smth if constructing key for writing data
      // writing "ANY" value
      writeAnyEncoded(rowKey, offset, stopKey);
    }
    return rowKey;
  }

  public byte[] getNextRowKey(List<TagValue> previousTags, byte[] rowKey) {
    byte[] newRowKey = new byte[rowKey.length];
    int offset = entityTable.getIdSize() + Bytes.SIZEOF_INT +  entityTable.getIdSize() * previousTags.size();
    byte[] stopKey = Bytes.stopKeyForPrefix(Arrays.copyOfRange(rowKey,
                                                               offset, offset + entityTable.getIdSize()));
    if (stopKey == null) {
      return null;
    }
    System.arraycopy(rowKey, 0, newRowKey, 0, offset);
    System.arraycopy(stopKey, 0, newRowKey, offset, stopKey.length);
    Arrays.fill(newRowKey, offset + stopKey.length, rowKey.length, (byte) 0);
    return  newRowKey;
  }

  private long roundToResolution(long ts) {
    return (ts / resolution) * resolution;
  }

  public byte[] createFuzzyRowMask(List<TagValue> tagValues, String measureName) {
    return createFuzzyRowMask(tagValues, measureName, false);
  }

  public byte[] createFuzzyRowMask(List<TagValue> tagValues, String measureName, boolean isSearch) {
    // See createRowKey for row format info
    byte[] mask = new byte[(tagValues.size() + 2) * entityTable.getIdSize() + Bytes.SIZEOF_INT];
    int offset;

    // agg group encoded is always provided for fuzzy row filter
    if (isSearch) {
      offset = writeEncodedFuzzyMask(mask, 0);
    } else {
      offset = writeEncodedFixedMask(mask, 0);
    }
    // time is defined by start/stop keys when scanning - we never include it in fuzzy filter
    offset = writeFuzzyMask(mask, offset, Bytes.SIZEOF_INT);

    for (TagValue tagValue : tagValues) {
      if (tagValue.getValue() != null) {
        offset = writeEncodedFixedMask(mask, offset);
      } else {
        offset = writeEncodedFuzzyMask(mask, offset);
      }
    }

    if (measureName != null) {
      writeEncodedFixedMask(mask, offset);
    } else {
      writeEncodedFuzzyMask(mask, offset);
    }
    return mask;
  }

  public byte[] createColumn(long ts) {
    long timestamp = roundToResolution(ts);
    int timeBase = getTimeBase(timestamp);

    return deltaCache[(int) ((ts - timeBase) / resolution)];
  }

  public String getMeasureName(byte[] rowKey) {
    // last encoded is measure name
    long encoded = readEncoded(rowKey, rowKey.length - entityTable.getIdSize());
    return entityTable.getName(encoded, TYPE_MEASURE_NAME);
  }

  public List<TagValue> getTagValues(byte[] rowKey) {
    // todo: in some cases, the client knows the agg group - so to optimize we can accept is as a parameter
    // first encoded is aggregation group
    long encodedAggGroup = readEncoded(rowKey, 0);
    String aggGroup = entityTable.getName(encodedAggGroup, TYPE_TAGS_GROUP);
    if (aggGroup == null) {
      // will never happen, unless data in entity table was corrupted or deleted
      LOG.warn("Could not decode agg group: " + encodedAggGroup);
      return Collections.emptyList();
    }
    if (aggGroup.isEmpty()) {
      return Collections.emptyList();
    }

    // aggregation group is defined by list of tag names concatenated with "." (see writeEncodedAggGroup for details)
    String[] tagNames = aggGroup.split("\\.");

    // todo: assert count of tag values is same as tag names?
    List<TagValue> tags = Lists.newArrayListWithCapacity(tagNames.length);
    for (int i = 0; i < tagNames.length; i++) {
      // tag values go right after encoded agg group and timebase (encoded as int)
      long encodedTagValue = readEncoded(rowKey, entityTable.getIdSize() *  (i + 1) + Bytes.SIZEOF_INT);
      String tagValue = entityTable.getName(encodedTagValue, tagNames[i]);
      tags.add(new TagValue(tagNames[i], tagValue));
    }

    return tags;
  }

  public long getTimestamp(byte[] rowKey, byte[] column) {
    // timebase is encoded as int after the encoded agg group
    int timebase = Bytes.toInt(rowKey, entityTable.getIdSize());
    // time leftover is encoded as 2 byte column name
    int leftover = Bytes.toShort(column) * resolution;

    return timebase + leftover;
  }

  private int writeEncodedAggGroup(List<TagValue> tagValues, byte[] rowKey, int offset) {
    // aggregation group is defined by list of tag names
    StringBuilder sb = new StringBuilder();
    for (TagValue tagValue : tagValues) {
      sb.append(tagValue.getTagName()).append(".");
    }

    return writeEncoded(TYPE_TAGS_GROUP, sb.toString(), rowKey, offset);
  }

  /**
   * @return incremented offset
   */
  private int writeEncoded(String type, String entity, byte[] destination, int offset) {
    long id = entityTable.getId(type, entity);
    int idSize = entityTable.getIdSize();
    while (idSize != 0) {
      idSize--;
      destination[offset + idSize] = (byte) (id & 0xff);
      id >>= 8;
    }

    return offset + entityTable.getIdSize();
  }

  /**
   * @return incremented offset
   */
  private int writeAnyEncoded(byte[] destination, int offset, boolean stopKey) {
    // all encoded ids start with 1, so all zeroes is special case to say "any" matches
    // todo: all zeroes - should we move to entity table somehow?
    int idSize = entityTable.getIdSize();
    while (idSize != 0) {
      idSize--;
      // 0xff is the biggest byte value (according to lexographical bytes comparator we use)
      destination[offset + idSize] = stopKey ? (byte) 0xff : 0;
    }

    return offset + entityTable.getIdSize();
  }

  private int writeFuzzyMask(byte[] destination, int offset, int length) {
    int count = length;
    while (count != 0) {
      count--;
      destination[offset + count] = 1;
    }

    return offset + length;
  }

  private int writeEncodedFixedMask(byte[] destination, int offset) {
    int idSize = entityTable.getIdSize();
    while (idSize != 0) {
      idSize--;
      destination[offset + idSize] = 0;
    }

    return offset + entityTable.getIdSize();
  }

  private int writeEncodedFuzzyMask(byte[] destination, int offset) {
    int idSize = entityTable.getIdSize();
    while (idSize != 0) {
      idSize--;
      destination[offset + idSize] = 1;
    }

    return offset + entityTable.getIdSize();
  }

  private long readEncoded(byte[] bytes, int offset) {
    long id = 0;
    int idSize = entityTable.getIdSize();
    for (int i = 0; i < idSize; i++) {
      id |= (bytes[offset + i] & 0xff) << ((idSize - i - 1) * 8);
    }
    return id;
  }

  /**
   * Returns timebase computed with the table setting for the given timestamp.
   */
  private int getTimeBase(long time) {
    // We are using 4 bytes timebase for row
    long timeBase = time / rollTimebaseInterval * rollTimebaseInterval;
    Preconditions.checkArgument(timeBase < 0x100000000L, "Timestamp is too large.");
    return (int) timeBase;
  }

  private byte[][] createDeltaCache(int rollTime) {
    byte[][] deltas = new byte[rollTime + 1][];

    for (int i = 0; i <= rollTime; i++) {
      deltas[i] = Bytes.toBytes((short) i);
    }
    return deltas;
  }
}