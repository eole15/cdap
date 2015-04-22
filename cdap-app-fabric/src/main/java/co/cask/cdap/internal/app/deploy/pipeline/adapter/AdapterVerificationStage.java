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

package co.cask.cdap.internal.app.deploy.pipeline.adapter;

import co.cask.cdap.app.ApplicationSpecification;
import co.cask.cdap.pipeline.AbstractStage;
import co.cask.cdap.proto.AdapterSpecification;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;

/**
 * This {@link co.cask.cdap.pipeline.Stage} is responsible for verifying an adapter specification.
 */
public class AdapterVerificationStage extends AbstractStage<AdapterSpecification> {
  private final ApplicationSpecification templateSpec;

  public AdapterVerificationStage(ApplicationSpecification templateSpec) {
    super(TypeToken.of(AdapterSpecification.class));
    this.templateSpec = templateSpec;
  }


  /**
   * Receives an input containing adapter specification and location
   * and verifies both.
   *
   * @param input An instance of {@link AdapterSpecification}
   */
  @Override
  public void process(AdapterSpecification input) throws Exception {
    Preconditions.checkNotNull(input);

    // if this adapter uses a workflow, a schedule should be set
    if (templateSpec.getWorkflows().size() == 1) {
      if (input.getScheduleSpec() == null) {
        throw new IllegalArgumentException("A schedule must be set for workflow adapters");
      }
    }

    // Emit the input to next stage.
    emit(input);
  }

}
