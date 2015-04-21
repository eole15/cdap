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

package co.cask.cdap.templates.etl.common;

import co.cask.cdap.templates.etl.api.Transform;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Executes Transforms one iteration at a time, tracking how many records were input into and output from
 * each transform.
 *
 * @param <IN> the type of input object to the first transform
 * @param <OUT> the type of object output by the last transform
 */
public class TransformExecutor<IN, OUT> {
  private final List<Transform> transforms;
  private final List<DefaultEmitter> emitters;

  public TransformExecutor(List<Transform> transforms, List<StageMetrics> transformMetrics) {
    int numTransforms = transforms.size();
    Preconditions.checkArgument(numTransforms == transformMetrics.size());
    this.transforms = Lists.newArrayListWithCapacity(numTransforms);
    this.emitters = Lists.newArrayListWithCapacity(numTransforms);
    for (int i = 0; i < numTransforms; i++) {
      StageMetrics stageMetrics = transformMetrics.get(i);
      this.transforms.add(new TrackedTransform(transforms.get(i), stageMetrics));
      this.emitters.add(new DefaultEmitter(stageMetrics));
    }
  }

  public Iterable<OUT> runOneIteration(IN input) throws Exception {
    if (transforms.isEmpty()) {
      return Lists.newArrayList((OUT) input);
    }

    Transform transform = transforms.get(0);
    DefaultEmitter currentEmitter = emitters.get(0);
    currentEmitter.reset();
    transform.transform(input, currentEmitter);

    DefaultEmitter previousEmitter = currentEmitter;

    for (int i = 1; i < transforms.size(); i++) {
      transform = transforms.get(i);
      currentEmitter = emitters.get(i);
      currentEmitter.reset();
      for (Object transformedVal : previousEmitter) {
        transform.transform(transformedVal, currentEmitter);
      }
      previousEmitter.reset();
      previousEmitter = currentEmitter;
    }

    return previousEmitter;
  }
}
