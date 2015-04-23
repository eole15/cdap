/*
 * Copyright © 2014-2015 Cask Data, Inc.
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

package co.cask.cdap.data2.transaction.queue.leveldb;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.queue.QueueName;
import co.cask.cdap.data2.dataset2.lib.table.leveldb.LevelDBTableService;
import co.cask.cdap.data2.transaction.queue.AbstractQueueAdmin;
import co.cask.cdap.data2.transaction.queue.NoopQueueConfigurer;
import co.cask.cdap.data2.transaction.queue.QueueConfigurer;
import co.cask.cdap.data2.transaction.queue.QueueConstants;
import co.cask.cdap.data2.util.TableId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static co.cask.cdap.data2.transaction.queue.QueueConstants.QueueType.QUEUE;

/**
 * admin for queues in leveldb.
 */
@Singleton
public class LevelDBQueueAdmin extends AbstractQueueAdmin {

  private static final Logger LOG = LoggerFactory.getLogger(LevelDBQueueAdmin.class);

  private final LevelDBTableService service;

  @Inject
  public LevelDBQueueAdmin(CConfiguration conf, LevelDBTableService service) {
    this(service, QUEUE);
  }

  protected LevelDBQueueAdmin(LevelDBTableService service, QueueConstants.QueueType type) {
    super(type);
    this.service = service;
  }

  /**
   * This determines whether dropping a queue is supported (by dropping the queue's table).
   */
  public boolean doDropTable(@SuppressWarnings("unused") QueueName queueName) {
    // no-op because this would drop all tables for the flow
    // todo: introduce a method dropAllFor(flow) or similar
    return false;
  }

  /**
   * This determines whether truncating a queue is supported (by truncating the queue's table).
   */
  public boolean doTruncateTable(@SuppressWarnings("unused") QueueName queueName) {
    // yes, this will truncate all queues of the flow. But it rarely makes sense to clear a single queue.
    // todo: introduce a method truncateAllFor(flow) or similar, and set this to false
    return true;
  }

  @Override
  public boolean exists(QueueName queueName) {
    try {
      String actualTableName = getActualTableName(queueName);
      service.getTable(actualTableName);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void create(QueueName queueName) throws Exception {
    String actualTableName = getActualTableName(queueName);
    service.ensureTableExists(actualTableName);
  }

  @Override
  public void create(QueueName queueName, @SuppressWarnings("unused") Properties props) throws Exception {
    create(queueName);
  }

  @Override
  public void truncate(QueueName queueName) throws Exception {
    // all queues for one flow are stored in same table, and we would clear all of them. this makes it optional.
    if (doTruncateTable(queueName)) {
      String actualTableName = getActualTableName(queueName);
      service.dropTable(actualTableName);
      service.ensureTableExists(actualTableName);
    } else {
      LOG.warn("truncate({}) on LevelDB queue table has no effect.", queueName);
    }
  }

  @Override
  public void clearAllForFlow(String namespaceId, String app, String flow) throws Exception {
    String tableName = getTableNameForFlow(namespaceId, app, flow);
    service.dropTable(tableName);
    service.ensureTableExists(tableName);
  }

  @Override
  public QueueConfigurer getQueueConfigurer(QueueName queueName) {
    return new NoopQueueConfigurer();
  }

  @Override
  public void dropAllForFlow(String namespaceId, String app, String flow) throws Exception {
    String tableName = getTableNameForFlow(namespaceId, app, flow);
    service.dropTable(tableName);
  }

  @Override
  public void dropAllInNamespace(String namespaceId) throws Exception {
    dropAllTablesWithPrefix(String.format("%s.%s.", namespaceId, unqualifiedTableNamePrefix));
  }

  @Override
  public void upgrade() throws Exception {
    // No-op
  }

  private void dropAllTablesWithPrefix(String tableNamePrefix) throws Exception {
    for (String tableName : service.list()) {
      if (tableName.startsWith(tableNamePrefix)) {
        service.dropTable(tableName);
      }
    }
  }

  public String getActualTableName(QueueName queueName) {
    return getTableNameForFlow(queueName.getFirstComponent(),
                               queueName.getSecondComponent(),
                               queueName.getThirdComponent());
  }

  protected String getTableNameForFlow(String namespaceId, String app, String flow) {
    TableId tableId = getDataTableId(namespaceId, app, flow);
    return String.format("%s.%s", tableId.getNamespace(), tableId.getTableName());
  }
}
