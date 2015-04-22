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

package co.cask.cdap.test.internal;

import co.cask.cdap.test.AbstractWorkerManager;
import co.cask.cdap.test.WorkerManager;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default implementation of {@link WorkerManager}
 */
public class DefaultWorkerManager extends AbstractWorkerManager {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultWorkerManager.class);

  private final DefaultApplicationManager.ProgramId programId;
  private final String appId;
  private final String workerId;

  private final AppFabricClient appFabricClient;
  private final DefaultApplicationManager applicationManager;
  private final String namespace;

  public DefaultWorkerManager(String namespace, DefaultApplicationManager.ProgramId programId,
                              AppFabricClient appFabricClient, DefaultApplicationManager applicationManager) {
    this.namespace = namespace;
    this.programId = programId;
    this.appId = programId.getApplicationId();
    this.workerId = programId.getProgramId();
    this.appFabricClient = appFabricClient;
    this.applicationManager = applicationManager;
  }

  @Override
  public void setInstances(int instances) {
    Preconditions.checkArgument(instances > 0, "Instance count should be > 0.");
    try {
      appFabricClient.setWorkerInstances(namespace, appId, workerId, instances);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public void stop() {
    applicationManager.stopProgram(programId);
  }

  @Override
  public boolean isRunning() {
    return applicationManager.isRunning(programId);
  }

  @Override
  public int getInstances() {
    return appFabricClient.getWorkerInstances(namespace, appId, workerId).getInstances();
  }
}
