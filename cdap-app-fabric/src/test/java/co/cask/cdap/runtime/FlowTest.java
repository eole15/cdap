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

package co.cask.cdap.runtime;

import co.cask.cdap.ArgumentCheckApp;
import co.cask.cdap.InvalidFlowOutputApp;
import co.cask.cdap.WordCountApp;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.app.program.Program;
import co.cask.cdap.app.runtime.ProgramController;
import co.cask.cdap.app.runtime.ProgramRunner;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.discovery.EndpointStrategy;
import co.cask.cdap.common.discovery.RandomEndpointStrategy;
import co.cask.cdap.common.exception.AlreadyExistsException;
import co.cask.cdap.common.exception.NamespaceCannotBeCreatedException;
import co.cask.cdap.common.queue.QueueName;
import co.cask.cdap.common.stream.StreamEventCodec;
import co.cask.cdap.data2.queue.QueueClientFactory;
import co.cask.cdap.data2.queue.QueueEntry;
import co.cask.cdap.data2.queue.QueueProducer;
import co.cask.cdap.internal.app.deploy.pipeline.ApplicationWithPrograms;
import co.cask.cdap.internal.app.namespace.NamespaceAdmin;
import co.cask.cdap.internal.app.runtime.BasicArguments;
import co.cask.cdap.internal.app.runtime.ProgramRunnerFactory;
import co.cask.cdap.internal.app.runtime.SimpleProgramOptions;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.test.SlowTests;
import co.cask.cdap.test.internal.AppFabricTestHelper;
import co.cask.cdap.test.internal.DefaultId;
import co.cask.tephra.Transaction;
import co.cask.tephra.TransactionAware;
import co.cask.tephra.TransactionSystemClient;
import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.twill.discovery.Discoverable;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.apache.twill.discovery.ServiceDiscovered;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Category(SlowTests.class)
public class FlowTest {

  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  private static final Supplier<File> TEMP_FOLDER_SUPPLIER = new Supplier<File>() {

    @Override
    public File get() {
      try {
        return tmpFolder.newFolder();
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }
  };

  private static final Logger LOG = LoggerFactory.getLogger(FlowTest.class);

  @BeforeClass
  public static void init() throws AlreadyExistsException, NamespaceCannotBeCreatedException {
    NamespaceAdmin namespaceAdmin = AppFabricTestHelper.getInjector().getInstance(NamespaceAdmin.class);
    namespaceAdmin.createNamespace(Constants.DEFAULT_NAMESPACE_META);
  }

  @Test
  public void testAppWithArgs() throws Exception {
   final ApplicationWithPrograms app = AppFabricTestHelper.deployApplicationWithManager(ArgumentCheckApp.class,
                                                                                        TEMP_FOLDER_SUPPLIER);
   ProgramRunnerFactory runnerFactory = AppFabricTestHelper.getInjector().getInstance(ProgramRunnerFactory.class);

    // Only running flow is good. But, in case of service, we need to send something to service as it's lazy loading
    List<ProgramController> controllers = Lists.newArrayList();
    for (final Program program : app.getPrograms()) {
      ProgramRunner runner = runnerFactory.create(ProgramRunnerFactory.Type.valueOf(program.getType().name()));
      controllers.add(runner.run(program, new SimpleProgramOptions(
        program.getName(), new BasicArguments(), new BasicArguments(ImmutableMap.of("arg", "test")))));
    }

    TimeUnit.SECONDS.sleep(1);

    Gson gson = new Gson();
    DiscoveryServiceClient discoveryServiceClient = AppFabricTestHelper.getInjector().
                                                    getInstance(DiscoveryServiceClient.class);
    Discoverable discoverable = discoveryServiceClient.discover(
      String.format("service.%s.%s.%s",
                    DefaultId.NAMESPACE.getId(), "ArgumentCheckApp", "SimpleService")).iterator().next();

    URL url = new URL(String.format("http://%s:%d/v3/namespaces/default/apps/%s/services/%s/methods/%s",
                                    discoverable.getSocketAddress().getHostName(),
                                    discoverable.getSocketAddress().getPort(),
                                    "ArgumentCheckApp",
                                    "SimpleService",
                                    "ping"));
    HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
    // this would fail had the service been started without the argument (initialize would have thrown)
    Assert.assertEquals(200, urlConn.getResponseCode());

    for (ProgramController controller : controllers) {
      controller.stop().get();
    }
  }

  @Test
  public void testFlow() throws Exception {
    final ApplicationWithPrograms app = AppFabricTestHelper.deployApplicationWithManager(WordCountApp.class,
                                                                                         TEMP_FOLDER_SUPPLIER);
    ProgramRunnerFactory runnerFactory = AppFabricTestHelper.getInjector().getInstance(ProgramRunnerFactory.class);

    List<ProgramController> controllers = Lists.newArrayList();

    for (final Program program : app.getPrograms()) {
      // running mapreduce is out of scope of this tests (there's separate unit-test for that)
      if (program.getType() == ProgramType.MAPREDUCE) {
        continue;
      }
      ProgramRunner runner = runnerFactory.create(ProgramRunnerFactory.Type.valueOf(program.getType().name()));
      controllers.add(runner.run(program, new SimpleProgramOptions(program)));
    }

    TimeUnit.SECONDS.sleep(1);

    TransactionSystemClient txSystemClient = AppFabricTestHelper.getInjector().
                                             getInstance(TransactionSystemClient.class);

    QueueName queueName = QueueName.fromStream(app.getId().getNamespaceId(), "text");
    QueueClientFactory queueClientFactory = AppFabricTestHelper.getInjector().getInstance(QueueClientFactory.class);
    QueueProducer producer = queueClientFactory.createProducer(queueName);

    // start tx to write in queue in tx
    Transaction tx = txSystemClient.startShort();
    ((TransactionAware) producer).startTx(tx);

    StreamEventCodec codec = new StreamEventCodec();
    for (int i = 0; i < 10; i++) {
      String msg = "Testing message " + i;
      StreamEvent event = new StreamEvent(ImmutableMap.<String, String>of(),
                                          ByteBuffer.wrap(msg.getBytes(Charsets.UTF_8)));
      producer.enqueue(new QueueEntry(codec.encodePayload(event)));
    }

    // commit tx
    ((TransactionAware) producer).commitTx();
    txSystemClient.commit(tx);

    // Query the service for at most 10 seconds for the expected result
    Gson gson = new Gson();
    DiscoveryServiceClient discoveryServiceClient = AppFabricTestHelper.getInjector().
      getInstance(DiscoveryServiceClient.class);
    ServiceDiscovered serviceDiscovered = discoveryServiceClient.discover(
      String.format("service.%s.%s.%s", DefaultId.NAMESPACE.getId(), "WordCountApp", "WordFrequencyService"));
    EndpointStrategy endpointStrategy = new RandomEndpointStrategy(serviceDiscovered);
    int trials = 0;
    while (trials++ < 10) {
      Discoverable discoverable = endpointStrategy.pick(2, TimeUnit.SECONDS);
      URL url = new URL(String.format("http://%s:%d/v3/namespaces/default/apps/%s/services/%s/methods/%s/%s",
                                      discoverable.getSocketAddress().getHostName(),
                                      discoverable.getSocketAddress().getPort(),
                                      "WordCountApp",
                                      "WordFrequencyService",
                                      "wordfreq",
                                      "text:Testing"));
      try {
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        Map<String, Long> responseContent = gson.fromJson(
          new InputStreamReader(urlConn.getInputStream(), Charsets.UTF_8),
          new TypeToken<Map<String, Long>>() { }.getType());

        LOG.info("Service response: " + responseContent);
        if (ImmutableMap.of("text:Testing", 10L).equals(responseContent)) {
          break;
        }

      } catch (Throwable t) {
        LOG.info("Exception when trying to query service.", t);
      }

      TimeUnit.SECONDS.sleep(1);
    }

    Assert.assertTrue(trials < 10);

    for (ProgramController controller : controllers) {
      controller.stop().get();
    }
  }

  @Test (expected = IllegalArgumentException.class)
  public void testInvalidOutputEmitter() throws Throwable {
    try {
      AppFabricTestHelper.deployApplicationWithManager(InvalidFlowOutputApp.class, TEMP_FOLDER_SUPPLIER);
    } catch (Exception e) {
      throw Throwables.getRootCause(e);
    }
  }
}
