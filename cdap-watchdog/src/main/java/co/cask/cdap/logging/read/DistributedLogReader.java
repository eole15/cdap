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

package co.cask.cdap.logging.read;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.logging.LoggingConfiguration;
import co.cask.cdap.logging.appender.kafka.KafkaTopic;
import co.cask.cdap.logging.appender.kafka.LoggingEventSerializer;
import co.cask.cdap.logging.appender.kafka.StringPartitioner;
import co.cask.cdap.logging.context.LoggingContextHelper;
import co.cask.cdap.logging.filter.AndFilter;
import co.cask.cdap.logging.filter.Filter;
import co.cask.cdap.logging.kafka.KafkaConsumer;
import co.cask.cdap.logging.serialize.LogSchema;
import co.cask.cdap.logging.write.FileMetaDataManager;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.avro.Schema;
import org.apache.twill.common.Threads;
import org.apache.twill.filesystem.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Reads logs in a distributed setup.
 */
@SuppressWarnings("FieldCanBeLocal")
public final class DistributedLogReader implements LogReader {
  private static final Logger LOG = LoggerFactory.getLogger(DistributedLogReader.class);

  private static final int MAX_THREAD_POOL_SIZE = 20;

  private final List<LoggingConfiguration.KafkaHost> seedBrokers;
  private final String topic;
  private final int numPartitions;
  private final LoggingEventSerializer serializer;
  private final FileMetaDataManager fileMetaDataManager;
  private final Schema schema;
  private final ExecutorService executor;
  private final StringPartitioner partitioner;

  private final int kafkaTailFetchTimeoutMs = 300;

  /**
   * Creates a DistributedLogReader object.
   * @param cConfig configuration object containing Kafka seed brokers and number of Kafka partitions for log topic.
   */
  @Inject
  public DistributedLogReader(CConfiguration cConfig, FileMetaDataManager fileMetaDataManager) {
    try {
      this.seedBrokers = LoggingConfiguration.getKafkaSeedBrokers(
        cConfig.get(LoggingConfiguration.KAFKA_SEED_BROKERS));
      Preconditions.checkArgument(!this.seedBrokers.isEmpty(), "Kafka seed brokers list is empty!");

      this.topic = KafkaTopic.getTopic();
      Preconditions.checkArgument(!this.topic.isEmpty(), "Kafka topic is emtpty!");

      this.numPartitions = cConfig.getInt(LoggingConfiguration.NUM_PARTITIONS, -1);
      Preconditions.checkArgument(this.numPartitions > 0,
                                  "numPartitions should be greater than 0. Got numPartitions=%s", this.numPartitions);
      this.partitioner = new StringPartitioner(numPartitions);

      this.serializer = new LoggingEventSerializer();

      this.fileMetaDataManager = fileMetaDataManager;

      this.schema = new LogSchema().getAvroSchema();
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }

    // Thread pool of size max MAX_THREAD_POOL_SIZE.
    // 60 seconds wait time before killing idle threads.
    // Keep no idle threads more than 60 seconds.
    // If max thread pool size reached, reject the new coming
    executor =
      new ThreadPoolExecutor(0, MAX_THREAD_POOL_SIZE,
                             60L, TimeUnit.SECONDS,
                             new SynchronousQueue<Runnable>(),
                             Threads.createDaemonThreadFactory("dist-log-reader-%d"),
                             new ThreadPoolExecutor.DiscardPolicy());
  }

  @Override
  public void getLogNext(final LoggingContext loggingContext, final LogOffset fromOffset, final int maxEvents,
                              final Filter filter, final Callback callback) {
    if (fromOffset.getKafkaOffset() < 0) {
      getLogPrev(loggingContext, fromOffset, maxEvents, filter, callback);
      return;
    }

    executor.submit(
      new Runnable() {
        @Override
        public void run() {
          int partition = partitioner.partition(loggingContext.getLogPartition(), numPartitions);

          callback.init();

          KafkaConsumer kafkaConsumer = new KafkaConsumer(seedBrokers, topic, partition, kafkaTailFetchTimeoutMs);
          try {
            Filter logFilter = new AndFilter(ImmutableList.of(LoggingContextHelper.createFilter(loggingContext),
                                                              filter));

            long latestOffset = kafkaConsumer.fetchOffset(KafkaConsumer.Offset.LATEST);
            long startOffset = fromOffset.getKafkaOffset() + 1;

            if (startOffset >= latestOffset) {
              // At end of events, nothing to return
              return;
            }

            fetchLogEvents(kafkaConsumer, logFilter, startOffset, latestOffset, maxEvents, callback);
          } catch (Throwable e) {
            LOG.error("Got exception: ", e);
            throw  Throwables.propagate(e);
          } finally {
            try {
              try {
                callback.close();
              } finally {
                kafkaConsumer.close();
              }
            } catch (IOException e) {
              LOG.error(String.format("Caught exception when closing KafkaConsumer for topic %s, partition %d",
                                      topic, partition), e);
            }
          }
        }
      }
    );
  }

  @Override
  public void getLogPrev(final LoggingContext loggingContext, final LogOffset fromOffset, final int maxEvents,
                              final Filter filter, final Callback callback) {
    executor.submit(
      new Runnable() {
        @Override
        public void run() {
          int partition = partitioner.partition(loggingContext.getLogPartition(), numPartitions);

          callback.init();

          KafkaConsumer kafkaConsumer = new KafkaConsumer(seedBrokers, topic, partition, kafkaTailFetchTimeoutMs);
          try {
            Filter logFilter = new AndFilter(ImmutableList.of(LoggingContextHelper.createFilter(loggingContext),
                                                              filter));

            long latestOffset = kafkaConsumer.fetchOffset(KafkaConsumer.Offset.LATEST);
            long earliestOffset = kafkaConsumer.fetchOffset(KafkaConsumer.Offset.EARLIEST);
            long stopOffset;
            long startOffset;

            if (fromOffset.getKafkaOffset() < 0)  {
              stopOffset = latestOffset;
            } else {
              stopOffset = fromOffset.getKafkaOffset();
            }
            startOffset = stopOffset - maxEvents;

            if (startOffset < earliestOffset) {
              startOffset = earliestOffset;
            }

            if (startOffset >= stopOffset || startOffset >= latestOffset) {
              // At end of kafka events, nothing to return
              return;
            }

            // Events between startOffset and stopOffset may not have the required logs we are looking for,
            // we'll need to return at least 1 log offset for next getLogPrev call to work.
            int fetchCount = 0;
            while (fetchCount == 0) {
              fetchCount = fetchLogEvents(kafkaConsumer, logFilter, startOffset, stopOffset, maxEvents, callback);
              stopOffset = startOffset;
              if (stopOffset <= earliestOffset) {
                // Truly no log messages found.
                break;
              }

              startOffset = stopOffset - maxEvents;
              if (startOffset < earliestOffset) {
                startOffset = earliestOffset;
              }
            }
          } catch (Throwable e) {
            LOG.error("Got exception: ", e);
            throw  Throwables.propagate(e);
          } finally {
            try {
              try {
                callback.close();
              } finally {
                kafkaConsumer.close();
              }
            } catch (IOException e) {
              LOG.error(String.format("Caught exception when closing KafkaConsumer for topic %s, partition %d",
                                      topic, partition), e);
            }
          }
        }
      }
    );
  }


  @Override
  public void getLog(final LoggingContext loggingContext, final long fromTimeMs, final long toTimeMs,
                          final Filter filter, final Callback callback) {
    executor.submit(
      new Runnable() {
        @Override
        public void run() {

          callback.init();
          try {
            Filter logFilter = new AndFilter(ImmutableList.of(LoggingContextHelper.createFilter(loggingContext),
                                                              filter));

            SortedMap<Long, Location> sortedFiles = fileMetaDataManager.listFiles(loggingContext);
            long prevInterval = -1;
            Location prevFile = null;
            List<Location> files = Lists.newArrayListWithExpectedSize(sortedFiles.size());
            for (Map.Entry<Long, Location> entry : sortedFiles.entrySet()) {
              if (entry.getKey() >= fromTimeMs && prevInterval != -1 && prevInterval < toTimeMs) {
                files.add(prevFile);
              }
              prevInterval = entry.getKey();
              prevFile = entry.getValue();
            }

            if (prevInterval != -1 && prevInterval < toTimeMs) {
              files.add(prevFile);
            }

            AvroFileReader avroFileReader = new AvroFileReader(schema);
            for (Location file : files) {
              avroFileReader.readLog(file, logFilter, fromTimeMs, toTimeMs,
                                        Integer.MAX_VALUE, callback);
            }
          } catch (Throwable e) {
            LOG.error("Got exception: ", e);
            throw  Throwables.propagate(e);
          } finally {
            callback.close();
          }
        }
      }
    );
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }

  private int fetchLogEvents(KafkaConsumer kafkaConsumer, Filter logFilter, long startOffset, long stopOffset,
                              int maxEvents, Callback callback) {
    KafkaCallback kafkaCallback = new KafkaCallback(logFilter, serializer, stopOffset, maxEvents, callback);

    while (kafkaCallback.getCount() < maxEvents && startOffset < stopOffset) {
      kafkaConsumer.fetchMessages(startOffset, kafkaCallback);
      long lastOffset = kafkaCallback.getLastOffset();

      // No more Kafka messages
      if (lastOffset == -1) {
        break;
      }
      startOffset = kafkaCallback.getLastOffset() + 1;
    }

    return kafkaCallback.getCount();
  }

  private static class KafkaCallback implements co.cask.cdap.logging.kafka.Callback {
    private final Filter logFilter;
    private final LoggingEventSerializer serializer;
    private final long stopOffset;
    private final int maxEvents;
    private final Callback callback;
    private long lastOffset = -1;
    private int count = 0;

    private KafkaCallback(Filter logFilter, LoggingEventSerializer serializer, long stopOffset, int maxEvents,
                          Callback callback) {
      this.logFilter = logFilter;
      this.serializer = serializer;
      this.stopOffset = stopOffset;
      this.maxEvents = maxEvents;
      this.callback = callback;
    }

    @Override
    public void handle(long offset, ByteBuffer msgBuffer) {
      ILoggingEvent event = serializer.fromBytes(msgBuffer);
      if (offset < stopOffset && count < maxEvents && logFilter.match(event)) {
        ++count;
        callback.handle(new LogEvent(event, new LogOffset(offset, event.getTimeStamp())));
      }
      lastOffset = offset;
    }

    public long getLastOffset() {
      return lastOffset;
    }

    public int getCount() {
      return count;
    }
  }
}
