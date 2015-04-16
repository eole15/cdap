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

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.logging.LoggingConfiguration;
import co.cask.cdap.logging.context.LoggingContextHelper;
import co.cask.cdap.logging.filter.AndFilter;
import co.cask.cdap.logging.filter.Filter;
import co.cask.cdap.logging.serialize.LogSchema;
import co.cask.cdap.logging.write.FileMetaDataManager;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.avro.Schema;
import org.apache.twill.common.Threads;
import org.apache.twill.filesystem.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads log events in single node setup.
 */
public class StandaloneLogReader implements LogReader {
  private static final Logger LOG = LoggerFactory.getLogger(StandaloneLogReader.class);

  private static final int MAX_THREAD_POOL_SIZE = 20;

  private final FileMetaDataManager fileMetaDataManager;
  private final Schema schema;
  private final ExecutorService executor;

  @Inject
  public StandaloneLogReader(CConfiguration cConf, FileMetaDataManager fileMetaDataManager) {
    String baseDir = cConf.get(LoggingConfiguration.LOG_BASE_DIR);
    Preconditions.checkNotNull(baseDir, "Log base dir cannot be null");

    try {
      this.schema = new LogSchema().getAvroSchema();
      this.fileMetaDataManager = fileMetaDataManager;

    } catch (Exception e) {
      LOG.error("Got exception", e);
      throw Throwables.propagate(e);
    }

    // Thread pool of size max MAX_THREAD_POOL_SIZE.
    // 60 seconds wait time before killing idle threads.
    // Keep no idle threads more than 60 seconds.
    // If max thread pool size reached, reject the new coming
    this.executor =
      new ThreadPoolExecutor(0, MAX_THREAD_POOL_SIZE,
                             60L, TimeUnit.SECONDS,
                             new SynchronousQueue<Runnable>(),
                             Threads.createDaemonThreadFactory("single-log-reader-%d"),
                             new ThreadPoolExecutor.DiscardPolicy());
  }

  @Override
  public void getLogNext(final LoggingContext loggingContext, final LogOffset fromOffset, final int maxEvents,
                              final Filter filter, final Callback callback) {
    if (fromOffset.getTime() < 0) {
      getLogPrev(loggingContext, fromOffset, maxEvents, filter, callback);
      return;
    }

    executor.submit(
      new Runnable() {
        @Override
        public void run() {
          callback.init();

          try {
            Filter logFilter = new AndFilter(ImmutableList.of(LoggingContextHelper.createFilter(loggingContext),
                                                              filter));
            long fromTimeMs = fromOffset.getTime() + 1;

            SortedMap<Long, Location> sortedFiles = fileMetaDataManager.listFiles(loggingContext);
            if (sortedFiles.isEmpty()) {
              return;
            }

            long prevInterval = -1;
            Location prevPath = null;
            List<Location> tailFiles = Lists.newArrayListWithExpectedSize(sortedFiles.size());
            for (Map.Entry<Long, Location> entry : sortedFiles.entrySet()) {
              if (entry.getKey() >= fromTimeMs && prevPath != null) {
                tailFiles.add(prevPath);
              }
              prevInterval = entry.getKey();
              prevPath = entry.getValue();
            }

            if (prevInterval != -1) {
              tailFiles.add(prevPath);
            }

            AvroFileReader logReader = new AvroFileReader(schema);
            CountingCallback countingCallback = new CountingCallback(callback);
            for (Location file : tailFiles) {
              logReader.readLog(file, logFilter, fromTimeMs, Long.MAX_VALUE, maxEvents - countingCallback.getCount(),
                                countingCallback);
              if (countingCallback.getCount() >= maxEvents) {
                break;
              }
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

  /**
   * Counts the number of times handle is called.
   */
  private static class CountingCallback implements Callback {
    private final Callback callback;
    private final AtomicInteger count = new AtomicInteger(0);

    private CountingCallback(Callback callback) {
      this.callback = callback;
    }

    @Override
    public void init() {
    }

    @Override
    public void handle(LogEvent event) {
      count.incrementAndGet();
      callback.handle(event);
    }

    public int getCount() {
      return count.get();
    }

    @Override
    public void close() {
    }
  }

  @Override
  public void getLogPrev(final LoggingContext loggingContext, final LogOffset fromOffset, final int maxEvents,
                              final Filter filter, final Callback callback) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        callback.init();
        try {
          Filter logFilter = new AndFilter(ImmutableList.of(LoggingContextHelper.createFilter(loggingContext),
                                                            filter));

          SortedMap<Long, Location> sortedFiles =
            ImmutableSortedMap.copyOf(fileMetaDataManager.listFiles(loggingContext), Collections.<Long>reverseOrder());
          if (sortedFiles.isEmpty()) {
            return;
          }

          long fromTimeMs = fromOffset.getTime() >= 0 ? fromOffset.getTime() - 1 : System.currentTimeMillis();

          List<Location> tailFiles = Lists.newArrayListWithExpectedSize(sortedFiles.size());
          for (Map.Entry<Long, Location> entry : sortedFiles.entrySet()) {
            if (entry.getKey() <= fromTimeMs) {
              tailFiles.add(entry.getValue());
            }
          }

          List<Collection<LogEvent>> logSegments = Lists.newLinkedList();
          AvroFileReader logReader = new AvroFileReader(schema);
          int count = 0;
          for (Location file : tailFiles) {
            Collection<LogEvent> events = logReader.readLogPrev(file, logFilter, fromTimeMs,
                                                                maxEvents - count);
            logSegments.add(events);
            count += events.size();
            if (count >= maxEvents) {
              break;
            }
          }

          for (LogEvent event : Iterables.concat(Lists.reverse(logSegments))) {
            callback.handle(event);
          }
        } catch (Throwable e) {
          LOG.error("Got exception: ", e);
          throw  Throwables.propagate(e);
        } finally {
          callback.close();
        }
      }
    });
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
            if (sortedFiles.isEmpty()) {
              return;
            }

            long prevInterval = -1;
            Location prevPath = null;
            List<Location> files = Lists.newArrayListWithExpectedSize(sortedFiles.size());
            for (Map.Entry<Long, Location> entry : sortedFiles.entrySet()) {
              if (entry.getKey() >= fromTimeMs && prevInterval != -1 && prevInterval < toTimeMs) {
                files.add(prevPath);
              }
              prevInterval = entry.getKey();
              prevPath = entry.getValue();
            }

            if (prevInterval != -1 && prevInterval < toTimeMs) {
              files.add(prevPath);
            }

            AvroFileReader avroFileReader = new AvroFileReader(schema);
            for (Location file : files) {
              avroFileReader.readLog(file, logFilter, fromTimeMs, toTimeMs, Integer.MAX_VALUE, callback);
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
}
