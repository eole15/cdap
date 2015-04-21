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

package co.cask.cdap.templates;

import co.cask.cdap.api.Resources;
import co.cask.cdap.api.data.stream.Stream;
import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.module.DatasetModule;
import co.cask.cdap.api.schedule.SchedulableProgramType;
import co.cask.cdap.api.schedule.Schedule;
import co.cask.cdap.api.schedule.ScheduleSpecification;
import co.cask.cdap.api.schedule.Schedules;
import co.cask.cdap.api.templates.AdapterConfigurer;
import co.cask.cdap.api.workflow.ScheduleProgramInfo;
import co.cask.cdap.app.ApplicationSpecification;
import co.cask.cdap.data.dataset.DatasetCreationSpec;
import co.cask.cdap.internal.schedule.StreamSizeSchedule;
import co.cask.cdap.internal.schedule.TimeSchedule;
import co.cask.cdap.proto.AdapterConfig;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramType;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Default configurer for adapters.
 */
public class DefaultAdapterConfigurer implements AdapterConfigurer {
  private final AdapterConfig adapterConfig;
  private final Map<String, StreamSpecification> streams = Maps.newHashMap();
  private final Map<String, String> dataSetModules = Maps.newHashMap();
  private final Map<String, DatasetCreationSpec> dataSetInstances = Maps.newHashMap();
  private final Map<String, String> runtimeArgs = Maps.newHashMap();
  private final ApplicationSpecification templateSpec;
  private final Id.Namespace namespaceId;
  private final ProgramType programType;
  private final String adapterName;
  private Schedule schedule;
  // only used if the adapter is using workers (i.e. schedule is null).
  private int instances;
  private Resources resources;

  // passed app to be used to resolve default name and description
  public DefaultAdapterConfigurer(Id.Namespace namespaceId, String adapterName, AdapterConfig adapterConfig,
                                  ApplicationSpecification templateSpec) {
    this.namespaceId = namespaceId;
    this.adapterName = adapterName;
    this.adapterConfig = adapterConfig;
    this.templateSpec = templateSpec;
    if (templateSpec.getWorkflows().size() == 1) {
      programType = ProgramType.WORKFLOW;
    } else if (templateSpec.getWorkers().size() == 1) {
      programType = ProgramType.WORKER;
    } else {
      // this should never happen. it is verified when a template is deployed.
      throw new IllegalArgumentException("Invalid adapter template. It must contain exactly one workflow or worker");
    }
    // defaults
    this.resources = new Resources();
    this.instances = 1;
  }

  @Override
  public void addStream(Stream stream) {
    Preconditions.checkArgument(stream != null, "Stream cannot be null.");
    StreamSpecification spec = stream.configure();
    streams.put(spec.getName(), spec);
  }

  @Override
  public void addDatasetModule(String moduleName, Class<? extends DatasetModule> moduleClass) {
    Preconditions.checkArgument(moduleName != null, "Dataset module name cannot be null.");
    Preconditions.checkArgument(moduleClass != null, "Dataset module class cannot be null.");
    dataSetModules.put(moduleName, moduleClass.getName());
  }

  @Override
  public void addDatasetType(Class<? extends Dataset> datasetClass) {
    Preconditions.checkArgument(datasetClass != null, "Dataset class cannot be null.");
    dataSetModules.put(datasetClass.getName(), datasetClass.getName());
  }

  @Override
  public void createDataset(String datasetInstanceName, String typeName, DatasetProperties properties) {
    Preconditions.checkArgument(datasetInstanceName != null, "Dataset instance name cannot be null.");
    Preconditions.checkArgument(typeName != null, "Dataset type name cannot be null.");
    Preconditions.checkArgument(properties != null, "Instance properties name cannot be null.");
    dataSetInstances.put(datasetInstanceName,
                         new DatasetCreationSpec(datasetInstanceName, typeName, properties));
  }

  @Override
  public void createDataset(String datasetInstanceName,
                            Class<? extends Dataset> datasetClass,
                            DatasetProperties properties) {

    Preconditions.checkArgument(datasetInstanceName != null, "Dataset instance name cannot be null.");
    Preconditions.checkArgument(datasetClass != null, "Dataset class name cannot be null.");
    Preconditions.checkArgument(properties != null, "Instance properties name cannot be null.");
    dataSetInstances.put(datasetInstanceName,
                         new DatasetCreationSpec(datasetInstanceName, datasetClass.getName(), properties));
    dataSetModules.put(datasetClass.getName(), datasetClass.getName());
  }

  @Override
  public void setSchedule(Schedule schedule) {
    Preconditions.checkNotNull(schedule, "Schedule cannot be null.");
    Preconditions.checkNotNull(schedule.getName(), "Schedule name cannot be null.");
    Preconditions.checkArgument(!schedule.getName().isEmpty(), "Schedule name cannot be empty.");
    Schedule realSchedule = schedule;
    // prefix with the adapter name so that schedule names are unique
    String realScheduleName = adapterName + "." + schedule.getName();

    if (schedule.getClass().equals(Schedule.class) || schedule instanceof TimeSchedule) {
      realSchedule = Schedules.createTimeSchedule(realScheduleName, schedule.getDescription(),
                                                  schedule.getCronEntry());
    }
    if (schedule instanceof StreamSizeSchedule) {
      StreamSizeSchedule dataSchedule = (StreamSizeSchedule) schedule;
      Preconditions.checkArgument(dataSchedule.getDataTriggerMB() > 0,
                                  "Schedule data trigger must be greater than 0.");
      realSchedule = Schedules.createDataSchedule(realScheduleName, dataSchedule.getDescription(),
                                                  Schedules.Source.STREAM, dataSchedule.getStreamName(),
                                                  dataSchedule.getDataTriggerMB());
    }

    this.schedule = realSchedule;
  }

  @Override
  public void setResources(Resources resources) {
    this.resources = resources;
  }

  @Override
  public void setInstances(int instances) {
    this.instances = instances;
  }

  @Override
  public void addRuntimeArguments(Map<String, String> arguments) {
    runtimeArgs.putAll(arguments);
  }

  @Override
  public void addRuntimeArgument(String key, String value) {
    runtimeArgs.put(key, value);
  }

  public AdapterSpecification createSpecification() {
    ScheduleSpecification scheduleSpec = null;
    String programName;
    if (programType == ProgramType.WORKFLOW) {
      programName = Iterables.getFirst(templateSpec.getWorkflows().keySet(), null);
      scheduleSpec = new ScheduleSpecification(schedule, new ScheduleProgramInfo(
        SchedulableProgramType.WORKFLOW, programName), runtimeArgs);
    } else {
      programName = Iterables.getFirst(templateSpec.getWorkers().keySet(), null);
    }

    Id.Program program = Id.Program.from(namespaceId, adapterConfig.getTemplate(), programType, programName);

    AdapterSpecification.Builder builder =
      AdapterSpecification.builder(adapterName, program)
        .setDescription(adapterConfig.getDescription())
        .setConfig(adapterConfig.getConfig())
        .setDatasets(dataSetInstances)
        .setDatasetModules(dataSetModules)
        .setStreams(streams)
        .setRuntimeArgs(runtimeArgs)
        .setScheduleSpec(scheduleSpec)
        .setInstances(instances)
        .setResources(resources);
    return builder.build();
  }
}
