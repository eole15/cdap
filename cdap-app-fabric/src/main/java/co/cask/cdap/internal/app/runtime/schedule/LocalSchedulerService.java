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

package co.cask.cdap.internal.app.runtime.schedule;

import co.cask.cdap.app.runtime.ProgramRuntimeService;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.config.PreferencesStore;
import com.google.common.base.Supplier;
import com.google.inject.Inject;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LocalSchedulerService - noop for pre and post hooks.
 */
public final class LocalSchedulerService extends AbstractSchedulerService {

  private static final Logger LOG = LoggerFactory.getLogger(LocalSchedulerService.class);

  @Inject
  public LocalSchedulerService(TimeScheduler timeScheduler, StreamSizeScheduler streamSizeScheduler,
                               CConfiguration cConf, Store store) {
    super(timeScheduler, streamSizeScheduler, cConf, store);
  }

  @Override
  protected void startUp() throws Exception {
    startSchedulers();
  }

  @Override
  protected void shutDown() throws Exception {
    stopScheduler();
  }
}
