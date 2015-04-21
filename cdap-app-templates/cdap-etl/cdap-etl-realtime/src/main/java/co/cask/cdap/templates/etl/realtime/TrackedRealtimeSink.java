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

package co.cask.cdap.templates.etl.realtime;

import co.cask.cdap.api.metrics.Metrics;
import co.cask.cdap.templates.etl.api.realtime.DataWriter;
import co.cask.cdap.templates.etl.api.realtime.RealtimeSink;
import co.cask.cdap.templates.etl.common.StageMetrics;

/**
 * Realtime sink that tracks how many records it wrote by emitting metrics.
 *
 * @param <T> the type of object to write
 */
public class TrackedRealtimeSink<T> extends RealtimeSink<T> {
  private final RealtimeSink<T> sink;
  private final Metrics metrics;

  public TrackedRealtimeSink(RealtimeSink<T> sink, Metrics metrics, String name) {
    this.sink = sink;
    this.metrics = new StageMetrics(metrics, StageMetrics.Type.SINK, name);
  }

  @Override
  public int write(Iterable<T> objects, DataWriter dataWriter) throws Exception {
    int written = sink.write(objects, dataWriter);
    metrics.count("records.out", written);
    return written;
  }
}
