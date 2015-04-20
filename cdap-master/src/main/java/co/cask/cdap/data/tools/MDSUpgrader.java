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

package co.cask.cdap.data.tools;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.table.Table;
import co.cask.cdap.app.ApplicationSpecification;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.namespace.NamespacedLocationFactory;
import co.cask.cdap.data2.datafabric.dataset.DatasetsUtil;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.lib.table.MDSKey;
import co.cask.cdap.data2.dataset2.lib.table.MetadataStoreDataset;
import co.cask.cdap.data2.dataset2.tx.Transactional;
import co.cask.cdap.data2.util.TableId;
import co.cask.cdap.internal.app.ApplicationSpecificationAdapter;
import co.cask.cdap.internal.app.store.AppMetadataStore;
import co.cask.cdap.internal.app.store.ApplicationMeta;
import co.cask.cdap.internal.app.store.DefaultStore;
import co.cask.cdap.internal.app.store.ProgramArgs;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.RunRecord;
import co.cask.tephra.TransactionExecutor;
import co.cask.tephra.TransactionExecutorFactory;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterators;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.twill.filesystem.LocationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Upgraded the Meta Data for applications
 */
public class MDSUpgrader extends AbstractUpgrader {

  private static final Logger LOG = LoggerFactory.getLogger(MDSUpgrader.class);
  private static final Gson GSON = ApplicationSpecificationAdapter.addTypeAdapters(new GsonBuilder()).create();

  private final Transactional<AppMDS, MetadataStoreDataset> appMDS;
  private final Store store;
  private final TableId appMetaTableId;

  @Inject
  private MDSUpgrader(LocationFactory locationFactory, NamespacedLocationFactory namespacedLocationFactory,
                      TransactionExecutorFactory executorFactory, final DatasetFramework dsFramework,
                      @Named("defaultStore") final Store store) {
    super(locationFactory, namespacedLocationFactory);
    this.store = store;
    final String appMetaTableName = Joiner.on(".").join(Constants.SYSTEM_NAMESPACE, DefaultStore.APP_META_TABLE);
    this.appMDS = Transactional.of(executorFactory, new Supplier<AppMDS>() {
      @Override
      public AppMDS get() {
        try {
          Table table = DatasetsUtil.getOrCreateDataset(dsFramework,
                                                        Id.DatasetInstance.from(Constants.DEFAULT_NAMESPACE_ID,
                                                                                appMetaTableName),
                                                        "table", DatasetProperties.EMPTY,
                                                        DatasetDefinition.NO_ARGUMENTS, null);
          return new AppMDS(new AppMetadataStoreDataset(table));
        } catch (Exception e) {
          LOG.error("Failed to access {} table", appMetaTableName, e);
          throw Throwables.propagate(e);
        }
      }
    });
    this.appMetaTableId = TableId.from(Constants.DEFAULT_NAMESPACE_ID, appMetaTableName);
  }

  @Override
  public void upgrade() throws Exception {
    appMDS.executeUnchecked(new TransactionExecutor.
      Function<AppMDS, Void>() {
      @Override
      public Void apply(AppMDS appMetaStore) throws Exception {
        MDSKey appMetaRecordPrefix = new MDSKey.Builder().add(AppMetadataStore.TYPE_APP_META).build();
        List<ApplicationMeta> appsMeta = appMetaStore.mds.list(appMetaRecordPrefix, ApplicationMeta.class);
        for (ApplicationMeta curAppMeta : appsMeta) {
          handleAppSpec(curAppMeta.getId(), curAppMeta.getSpec());
        }
        return null;
      }
    });

    // Scans for all the streams in the meta table
    appMDS.executeUnchecked(new TransactionExecutor.
      Function<AppMDS, Void>() {
      @Override
      public Void apply(AppMDS appMetaStore) throws Exception {
        MDSKey streamRecordPrefix = new MDSKey.Builder().add(AppMetadataStore.TYPE_STREAM).build();
        List<StreamSpecification> streamsSpec = appMetaStore.mds.list(streamRecordPrefix, StreamSpecification.class);
        for (StreamSpecification curStreamSpec : streamsSpec) {
          store.addStream(Id.Namespace.from(Constants.DEFAULT_NAMESPACE), curStreamSpec);
        }
        return null;
      }
    });
  }

  /**
   * For an application calls {@link MDSUpgrader#handlePrograms} for the different program types whose meta data
   * might exist in the meta table and will need upgrade
   *
   * @param appId the application id
   * @param appSpec the {@link ApplicationSpecification} of the application
   */
  private void handleAppSpec(String appId, ApplicationSpecification appSpec) {
    handlePrograms(appId, appSpec.getFlows().keySet(), ProgramType.FLOW);
    handlePrograms(appId, appSpec.getMapReduce().keySet(), ProgramType.MAPREDUCE);
    handlePrograms(appId, appSpec.getSpark().keySet(), ProgramType.SPARK);
    handlePrograms(appId, appSpec.getWorkflows().keySet(), ProgramType.WORKFLOW);
    handlePrograms(appId, appSpec.getServices().keySet(), ProgramType.SERVICE);
  }

  /**
   * Calls different functions to upgrade different kind of meta data of a program
   *
   * @param appId the application id
   * @param programIds the program ids for the programs in this application
   * @param programType the {@link ProgramType} of these program
   */
  private void handlePrograms(String appId, Set<String> programIds, ProgramType programType) {
    for (String programId : programIds) {
      handleRunRecordStarted(appId, programId, programType);
      handleRunRecordCompleted(appId, programId, programType);
      handleProgramArgs(appId, programId, programType);
    }
  }

  /**
   * Handles the {@link AppMetadataStore#TYPE_PROGRAM_ARGS} meta data and writes it back with namespace
   *
   * @param appId the application id to which this program belongs to
   * @param programId the program id of the program
   * @param programType the {@link ProgramType} of the program
   */
  private void handleProgramArgs(final String appId, final String programId, final ProgramType programType) {
    final MDSKey partialKey = new MDSKey.Builder().add(AppMetadataStore.TYPE_PROGRAM_ARGS, Constants.DEVELOPER_ACCOUNT,
                                                       appId, programId).build();
    appMDS.executeUnchecked(new TransactionExecutor.Function<AppMDS, Void>() {
      @Override
      public Void apply(AppMDS appMetaStore) throws Exception {
        List<ProgramArgs> programsArgs = appMetaStore.mds.list(partialKey, ProgramArgs.class);
        for (ProgramArgs curProgramArgs : programsArgs) {
          store.storeRunArguments(Id.Program.from(Id.Application.from(Constants.DEFAULT_NAMESPACE, appId), programType,
                                                  programId), curProgramArgs.getArgs());
        }
        return null;
      }
    });
  }

  /**
   * Handles the {@link AppMetadataStore#TYPE_RUN_RECORD_STARTED} meta data and writes it back with namespace
   *
   * @param appId the application id to which this program belongs to
   * @param programId the program id of the program
   * @param programType the {@link ProgramType} of the program
   */
  private void handleRunRecordStarted(final String appId, final String programId, final ProgramType programType) {
    final MDSKey partialKey =
      new MDSKey.Builder().add(AppMetadataStore.TYPE_RUN_RECORD_STARTED, Constants.DEVELOPER_ACCOUNT,
                               appId, programId).build();
    appMDS.executeUnchecked(new TransactionExecutor.Function<AppMDS, Void>() {
      @Override
      public Void apply(AppMDS appMetaStore) throws Exception {
        List<RunRecord> startedRunRecords = appMetaStore.mds.list(partialKey, RunRecord.class);
        for (RunRecord curStartedRunRecord : startedRunRecords) {
          store.setStart(Id.Program.from(Id.Application.from(Constants.DEFAULT_NAMESPACE, appId), programType,
                                         programId), curStartedRunRecord.getPid(), curStartedRunRecord.getStartTs());
        }
        return null;
      }
    });
  }

  /**
   * Handles the {@link AppMetadataStore#TYPE_RUN_RECORD_COMPLETED} meta data and writes it back with namespace
   *
   * @param appId the application id to which this program belongs to
   * @param programId the program id of the program
   * @param programType the {@link ProgramType} of the program
   */
  private void handleRunRecordCompleted(final String appId, final String programId, final ProgramType programType) {

    final MDSKey partialKey =
      new MDSKey.Builder().add(AppMetadataStore.TYPE_RUN_RECORD_COMPLETED, Constants.DEVELOPER_ACCOUNT,
                               appId, programId).build();
    appMDS.executeUnchecked(new TransactionExecutor.Function<AppMDS, Void>() {
      @Override
      public Void apply(AppMDS appMetaStore) throws Exception {
        List<RunRecord> completedRunRecords = appMetaStore.mds.list(partialKey, RunRecord.class);
        for (RunRecord curCompletedRunRecord : completedRunRecords) {
          // we cannot add a runRecordCompleted if there is no runRecordStarted for the run so write it first
          // the next setStop call deletes the runRecordStarted for that run so we don't have to delete it here.
          writeTempRunRecordStart(appId, programType, programId, curCompletedRunRecord.getPid(),
                                  curCompletedRunRecord.getStartTs());
          store.setStop(Id.Program.from(Id.Application.from(Constants.DEFAULT_NAMESPACE, appId), programType,
                                        programId), curCompletedRunRecord.getPid(), curCompletedRunRecord.getStopTs(),
                        curCompletedRunRecord.getStatus());
        }
        return null;
      }
    });
  }

  /**
   * Writes the {@link AppMetadataStore#TYPE_RUN_RECORD_STARTED} entry in the app meta table so that
   * {@link AppMetadataStore#TYPE_RUN_RECORD_COMPLETED} can be written which deleted the started record.
   *
   * @param appId the application id
   * @param programType {@link ProgramType} of this program
   * @param programId the program id of this program
   * @param pId the process id of the run
   * @param startTs the startTs
   */
  private void writeTempRunRecordStart(String appId, ProgramType programType, String programId, String pId,
                                       long startTs) {
    store.setStart(Id.Program.from(Id.Application.from(Constants.DEFAULT_NAMESPACE, appId), programType, programId),
                   pId, startTs);
  }

  private static final class AppMDS implements Iterable<MetadataStoreDataset> {
    private final MetadataStoreDataset mds;

    private AppMDS(MetadataStoreDataset metaTable) {
      this.mds = metaTable;
    }

    @Override
    public Iterator<MetadataStoreDataset> iterator() {
      return Iterators.singletonIterator(mds);
    }
  }

  public TableId getOldAppMetaTableId() {
    return appMetaTableId;
  }

  /**
   * Extends {@link MetadataStoreDataset} to provide a custom {@link Gson} which has adapters for
   * {@link ApplicationSpecification} needed for deserialization.
   */
  private static class AppMetadataStoreDataset extends MetadataStoreDataset {
    public AppMetadataStoreDataset(Table table) {
      super(table);
    }

    @Override
    protected <T> T deserialize(byte[] serialized, Type typeOfT) {
      return GSON.fromJson(Bytes.toString(serialized), typeOfT);
    }
  }
}
