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

package co.cask.cdap.gateway.handlers.log;

import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.gateway.handlers.metrics.MetricsSuiteTestBase;
import co.cask.cdap.logging.read.LogOffset;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Test LogHandler.
 */
public class LogHandlerTestRun extends MetricsSuiteTestBase {
  private static final Type LIST_LOGLINE_TYPE = new TypeToken<List<LogLine>>() { }.getType();
  private static final Gson GSON = new Gson();

  @Test
  public void testFlowNext() throws Exception {
    testNext("testApp1", "flows", "testFlow1", true);
    testNextNoMax("testApp1", "flows", "testFlow1");
    testNextFilter("testApp1", "flows", "testFlow1");
    testNextNoFrom("testApp1", "flows", "testFlow1");
    testNext("testApp1", "flows", "testFlow1", false);
  }

  @Test
  public void testServiceNext() throws Exception {
    testNext("testApp4", "services", "testService1", true);
    testNextNoMax("testApp4", "services", "testService1");
    testNextFilter("testApp4", "services", "testService1");
    testNextNoFrom("testApp4", "services", "testService1");
    testNext("testApp4", "services", "testService1", false);
  }

  @Test
  public void testProcedureNext() throws Exception {
    testNext("testApp2", "procedures", "testProcedure1", true);
    testNextNoMax("testApp2", "procedures", "testProcedure1");
    testNextFilter("testApp2", "procedures", "testProcedure1");
    testNextNoFrom("testApp2", "procedures", "testProcedure1");
  }

  @Test
  public void testMapReduceNext() throws Exception {
    testNext("testApp3", "mapreduce", "testMapReduce1", true);
    testNextNoMax("testApp3", "mapreduce", "testMapReduce1");
    testNextFilter("testApp3", "mapreduce", "testMapReduce1");
    testNextNoFrom("testApp3", "mapreduce", "testMapReduce1");
  }

  @Test
  public void testFlowPrev() throws Exception {
    testPrev("testApp1", "flows", "testFlow1");
    testPrevNoMax("testApp1", "flows", "testFlow1");
    testPrevFilter("testApp1", "flows", "testFlow1");
    testPrevNoFrom("testApp1", "flows", "testFlow1");
  }

  @Test
  public void testServicePrev() throws Exception {
    testPrev("testApp4", "services", "testService1");
    testPrevNoMax("testApp4", "services", "testService1");
    testPrevFilter("testApp4", "services", "testService1");
    testPrevNoFrom("testApp4", "services", "testService1");
  }

  @Test
  public void testProcedurePrev() throws Exception {
    testPrev("testApp2", "procedures", "testProcedure1");
    testPrevNoMax("testApp2", "procedures", "testProcedure1");
    testPrevFilter("testApp2", "procedures", "testProcedure1");
    testPrevNoFrom("testApp2", "procedures", "testProcedure1");
  }

  @Test
  public void testMapReducePrev() throws Exception {
    testPrev("testApp3", "mapreduce", "testMapReduce1");
    testPrevNoMax("testApp3", "mapreduce", "testMapReduce1");
    testPrevFilter("testApp3", "mapreduce", "testMapReduce1");
    testPrevNoFrom("testApp3", "mapreduce", "testMapReduce1");
  }

  @Test
  public void testFlowLogs() throws Exception {
    testLogs("testApp1", "flows", "testFlow1");
    testLogsFilter("testApp1", "flows", "testFlow1");
  }

  @Test
  public void testServiceLogs() throws Exception {
    testLogs("testApp4", "services", "testService1");
    testLogsFilter("testApp4", "services", "testService1");
  }

  @Test
  public void testProcedureLogs() throws Exception {
    testLogs("testApp2", "procedures", "testProcedure1");
    testLogsFilter("testApp2", "procedures", "testProcedure1");
  }

  @Test
  public void testMapReduceLogs() throws Exception {
    testLogs("testApp3", "mapreduce", "testMapReduce1");
    testLogsFilter("testApp3", "mapreduce", "testMapReduce1");
  }

  @Test
  public void testFlowNextV3() throws Exception {
    testNext("testApp1", "flows", "testFlow1", true, Constants.Gateway.API_VERSION_3_TOKEN,
             MockLogReader.TEST_NAMESPACE);
    testNextNoMax("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                  MockLogReader.TEST_NAMESPACE);
    testNextFilter("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
    testNextNoFrom("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
    testNext("testApp1", "flows", "testFlow1", false);
    testNextRunId("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                  MockLogReader.TEST_NAMESPACE);
  }

  @Test
  public void testServiceNextV3() throws Exception {
    testNext("testApp4", "services", "testService1", true, Constants.Gateway.API_VERSION_3_TOKEN,
             MockLogReader.TEST_NAMESPACE);
    testNextNoMax("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
                  MockLogReader.TEST_NAMESPACE);
    testNextFilter("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
    testNextNoFrom("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
    testNext("testApp4", "services", "testService1", false, Constants.Gateway.API_VERSION_3_TOKEN,
             MockLogReader.TEST_NAMESPACE);
  }

  @Test
  public void testMapReduceNextV3() throws Exception {
    testNext("testApp3", "mapreduce", "testMapReduce1", true, Constants.Gateway.API_VERSION_3_TOKEN,
             Constants.DEFAULT_NAMESPACE);
    try {
      testNext("testApp3", "mapreduce", "testMapReduce1", true, Constants.Gateway.API_VERSION_3_TOKEN,
               MockLogReader.TEST_NAMESPACE);
      Assert.fail();
    } catch (AssertionError e) {
      // this must fail
    }
    testNextNoMax("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                  Constants.DEFAULT_NAMESPACE);
    testNextFilter("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                   Constants.DEFAULT_NAMESPACE);
    testNextNoFrom("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                   Constants.DEFAULT_NAMESPACE);
  }

  @Test
  public void testFlowPrevV3() throws Exception {
    testPrev("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN, MockLogReader.TEST_NAMESPACE);
    testPrevNoMax("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                  MockLogReader.TEST_NAMESPACE);
    testPrevFilter("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
    testPrevNoFrom("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
    testPrevRunId("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                  MockLogReader.TEST_NAMESPACE);
  }

  @Test
  public void testServicePrevV3() throws Exception {
    testPrev("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
             MockLogReader.TEST_NAMESPACE);
    testPrevNoMax("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
                  MockLogReader.TEST_NAMESPACE);
    testPrevFilter("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
    testPrevNoFrom("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
  }

  @Test
  public void testMapReducePrevV3() throws Exception {
    testPrev("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
             Constants.DEFAULT_NAMESPACE);
    testPrevNoMax("testApp3", "mapreduce", "testMapReduce1");
    try {
      testPrevNoMax("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                    MockLogReader.TEST_NAMESPACE);
      Assert.fail();
    } catch (AssertionError e) {
      // this should fail.
    }
    testPrevFilter("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                   Constants.DEFAULT_NAMESPACE);
    testPrevNoFrom("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                   Constants.DEFAULT_NAMESPACE);
  }

  @Test
  public void testFlowLogsV3() throws Exception {
    testLogs("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
             MockLogReader.TEST_NAMESPACE);
    try {
      testLogs("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
               Constants.DEFAULT_NAMESPACE);
      Assert.fail();
    } catch (AssertionError e) {
      // should fail
    }
    testLogsFilter("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
    testLogsRunId("testApp1", "flows", "testFlow1", Constants.Gateway.API_VERSION_3_TOKEN,
                  MockLogReader.TEST_NAMESPACE);
  }

  @Test
  public void testServiceLogsV3() throws Exception {
    testLogs("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
             MockLogReader.TEST_NAMESPACE);
    testLogsFilter("testApp4", "services", "testService1", Constants.Gateway.API_VERSION_3_TOKEN,
                   MockLogReader.TEST_NAMESPACE);
  }

  @Test
  public void testMapReduceLogsV3() throws Exception {
    testLogs("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
             Constants.DEFAULT_NAMESPACE);
    testLogsFilter("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                   Constants.DEFAULT_NAMESPACE);
    try {
      testLogsFilter("testApp3", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                     Constants.DEFAULT_NAMESPACE);
      Assert.fail();
    } catch (AssertionError e) {
      // should fail
    }
  }

  @Test
  public void testAdapterLogs() throws Exception {
    testBatchAdapterLogs(MockLogReader.TEST_NAMESPACE, "testTemplate1", "testMapReduce1", "testAdapter1");
    // also test that the same logs appear as mapreduce logs as well
    testLogs("testTemplate1", "mapreduce", "testMapReduce1",
             Constants.Gateway.API_VERSION_3_TOKEN, MockLogReader.TEST_NAMESPACE);
    // in default namespace, mapreduce was run outside of adapter, so shouldn't find adapter logs
    // TODO: this verification is not quite accurate, need to simulate in a better way,
    // because testAdapter does not exist in default namespace
    try {
      testBatchAdapterLogs(Constants.DEFAULT_NAMESPACE, "testApp3", "testMapReduce1", "testAdapter1");
      Assert.fail();
    } catch (AssertionError e) {
      // Expected exception
    }
    testLogsRunId("testTemplate1", "mapreduce", "testMapReduce1", Constants.Gateway.API_VERSION_3_TOKEN,
                  MockLogReader.TEST_NAMESPACE);
  }

  @Test
  public void testSystemLogsV2() throws Exception {
    testPrevSystemLogs("v2", Constants.Service.APP_FABRIC_HTTP);
    testPrevSystemLogs("v2", Constants.Service.MASTER_SERVICES);

    testNextSystemLogs("v2", Constants.Service.APP_FABRIC_HTTP);
    testNextSystemLogs("v2", Constants.Service.MASTER_SERVICES);
  }

  @Test
  public void testSystemLogsV3() throws Exception {
    testPrevSystemLogs("v3", Constants.Service.APP_FABRIC_HTTP);
    testPrevSystemLogs("v3", Constants.Service.MASTER_SERVICES);
    testNextSystemLogs("v3", Constants.Service.APP_FABRIC_HTTP);
    testNextSystemLogs("v3", Constants.Service.MASTER_SERVICES);
  }

  private void testNext(String appId, String entityType, String entityId, boolean escape) throws Exception {
    testNext(appId, entityType, entityId, escape, null, null);
  }

  private void testNextNoMax(String appId, String entityType, String entityId) throws Exception {
    testNextNoMax(appId, entityType, entityId, null, null);
  }

  private void testNextFilter(String appId, String entityType, String entityId) throws Exception {
    testNextFilter(appId, entityType, entityId, null, null);
  }

  private void testNextNoFrom(String appId, String entityType, String entityId) throws Exception {
    testNextNoFrom(appId, entityType, entityId, null, null);
  }

  private void testPrev(String appId, String entityType, String entityId) throws Exception {
    testPrev(appId, entityType, entityId, null, null);
  }

  private void testPrevNoMax(String appId, String entityType, String entityId) throws Exception {
    testPrevNoMax(appId, entityType, entityId, null, null);
  }

  private void testPrevFilter(String appId, String entityType, String entityId) throws Exception {
    testPrevFilter(appId, entityType, entityId, null, null);
  }

  private void testPrevNoFrom(String appId, String entityType, String entityId) throws Exception {
    testPrevNoFrom(appId, entityType, entityId, null, null);
  }

  private void testLogs(String appId, String entityType, String entityId) throws Exception {
    testLogs(appId, entityType, entityId, null, null);
  }

  private void testLogsFilter(String appId, String entityType, String entityId) throws Exception {
    testLogsFilter(appId, entityType, entityId, null, null);
  }

  private void testNext(String appId, String entityType, String entityId, boolean escape, @Nullable String version,
                        @Nullable String namespace) throws Exception {
    String img = escape ? "&lt;img&gt;" : "<img>";
    String nextUrl = String.format("apps/%s/%s/%s/logs/next?fromOffset=%s&max=10&escape=%s",
                                   appId, entityType, entityId, getOffset(5), escape);
    HttpResponse response = doGet(getVersionedAPIPath(nextUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(10, logLines.size());
    int expected = 5;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + img + "-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected++;
    }
  }

  private void testNextNoMax(String appId, String entityType, String entityId, @Nullable String version,
                             @Nullable String namespace) throws Exception {
    String nextNoMaxUrl = String.format("apps/%s/%s/%s/logs/next?fromOffset=%s",
                                        appId, entityType, entityId, getOffset(10));
    HttpResponse response = doGet(getVersionedAPIPath(nextNoMaxUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(50, logLines.size());
    int expected = 10;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected++;
    }
  }

  private void testNextFilter(String appId, String entityType, String entityId, @Nullable String version,
                              @Nullable String namespace) throws Exception {
    String nextFilterUrl = String.format("apps/%s/%s/%s/logs/next?fromOffset=%s&max=16&filter=loglevel=ERROR",
                                         appId, entityType, entityId, getOffset(12));
    HttpResponse response = doGet(getVersionedAPIPath(nextFilterUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(8, logLines.size());
    int expected = 12;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected += 2;
    }
  }

  private void testNextNoFrom(String appId, String entityType, String entityId, @Nullable String version,
                              @Nullable String namespace) throws Exception {
    String nextNoFromUrl = String.format("apps/%s/%s/%s/logs/next", appId, entityType, entityId);
    HttpResponse response = doGet(getVersionedAPIPath(nextNoFromUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(50, logLines.size());
    int expected = 30;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected++;
    }
  }

  private void testNextRunId(String appId, String entityType, String entityId, @Nullable String version,
                             @Nullable String namespace) throws Exception {
    String nextNoFromUrl = String.format("apps/%s/%s/%s/runs/1/logs/next?max=100", appId, entityType, entityId);
    HttpResponse response = doGet(getVersionedAPIPath(nextNoFromUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(40, logLines.size());
    int expected = 1;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected += 2;
    }
  }

  private void testPrev(String appId, String entityType, String entityId, @Nullable String version,
                        @Nullable String namespace) throws Exception {
    String prevUrl = String.format("apps/%s/%s/%s/logs/prev?fromOffset=%s&max=10",
                                   appId, entityType, entityId, getOffset(25));
    HttpResponse response = doGet(getVersionedAPIPath(prevUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(10, logLines.size());
    int expected = 15;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected++;
    }
  }

  private void testPrevRunId(String appId, String entityType, String entityId, @Nullable String version,
                             @Nullable String namespace) throws Exception {
    String nextNoFromUrl = String.format("apps/%s/%s/%s/runs/0/logs/prev?max=100", appId, entityType, entityId);
    HttpResponse response = doGet(getVersionedAPIPath(nextNoFromUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(40, logLines.size());
    int expected = 0;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected += 2;
    }
  }

  private void testNextSystemLogs(String version, String serviceName) throws Exception {
    String prevUrl = String.format("/%s/system/services/%s/logs/next?max=10", version, serviceName);
    HttpResponse response = doGet(prevUrl);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
  }

  private void testPrevSystemLogs(String version, String serviceName) throws Exception {
    String prevUrl = String.format("/%s/system/services/%s/logs/prev?max=10", version, serviceName);
    HttpResponse response = doGet(prevUrl);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
  }

  private void testPrevNoMax(String appId, String entityType, String entityId, @Nullable String version,
                             @Nullable String namespace) throws Exception {
    String prevNoMaxUrl = String.format("apps/%s/%s/%s/logs/prev?fromOffset=%s",
                                        appId, entityType, entityId, getOffset(70));
    HttpResponse response = doGet(getVersionedAPIPath(prevNoMaxUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(50, logLines.size());
    int expected = 20;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected++;
    }
  }

  private void testPrevFilter(String appId, String entityType, String entityId, @Nullable String version,
                              @Nullable String namespace) throws Exception {
    String prevFilterUrl = String.format("apps/%s/%s/%s/logs/prev?fromOffset=%s&max=16&filter=loglevel=ERROR",
                                         appId, entityType, entityId, getOffset(41));
    HttpResponse response = doGet(getVersionedAPIPath(prevFilterUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(8, logLines.size());
    int expected = 26;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected += 2;
    }
  }

  private void testPrevNoFrom(String appId, String entityType, String entityId, @Nullable String version,
                              @Nullable String namespace) throws Exception {
    String prevNoFrom = String.format("apps/%s/%s/%s/logs/prev", appId, entityType, entityId);
    HttpResponse response = doGet(getVersionedAPIPath(prevNoFrom, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<LogLine> logLines = GSON.fromJson(out, LIST_LOGLINE_TYPE);
    Assert.assertEquals(50, logLines.size());
    int expected = 30;
    for (LogLine logLine : logLines) {
      Assert.assertEquals(expected, logLine.getOffset().getKafkaOffset());
      Assert.assertEquals(expected, logLine.getOffset().getTime());
      String expectedStr = entityId + "&lt;img&gt;-" + expected + "\n";
      String log = logLine.getLog();
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected++;
    }
  }

  private void testLogs(String appId, String entityType, String entityId, @Nullable String version,
                        @Nullable String namespace) throws Exception {
    String logsUrl = String.format("apps/%s/%s/%s/logs?start=20&stop=35", appId, entityType, entityId);
    HttpResponse response = doGet(getVersionedAPIPath(logsUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<String> logLines = Lists.newArrayList(Splitter.on("\n").omitEmptyStrings().split(out));
    Assert.assertEquals(15, logLines.size());
    int expected = 20;
    for (String log : logLines) {
      String expectedStr = entityId + "&lt;img&gt;-" + expected;
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected++;
    }
  }

  private void testLogsRunId(String appId, String entityType, String entityId, @Nullable String version,
                             @Nullable String namespace) throws Exception {
    String nextNoFromUrl = String.format("apps/%s/%s/%s/runs/0/logs?start=0&stop=100", appId, entityType, entityId);
    HttpResponse response = doGet(getVersionedAPIPath(nextNoFromUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<String> logLines = Lists.newArrayList(Splitter.on("\n").omitEmptyStrings().split(out));
    Assert.assertEquals(40, logLines.size());
    int expected = 0;
    for (String log : logLines) {
      String expectedStr = entityId + "&lt;img&gt;-" + expected;
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected += 2;
    }
  }

  private void testLogsFilter(String appId, String entityType, String entityId, @Nullable String version,
                              @Nullable String namespace) throws Exception {
    String logsFilterUrl = String.format("apps/%s/%s/%s/logs?start=20&stop=35&filter=loglevel=ERROR", appId, entityType,
                                         entityId);
    HttpResponse response = doGet(getVersionedAPIPath(logsFilterUrl, version, namespace));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<String> logLines = Lists.newArrayList(Splitter.on("\n").omitEmptyStrings().split(out));
    Assert.assertEquals(8, logLines.size());
    int expected = 20;
    for (String log : logLines) {
      String expectedStr = entityId + "&lt;img&gt;-" + expected;
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected += 2;
    }
  }

  private void testBatchAdapterLogs(String namespaceId, String templateId, String mrName,
                                    String adapterName) throws Exception {
    String logsUrl = String.format("adapters/%s/logs?template=%s&programtype=%s&programid=%s&start=20&stop=35",
                                   adapterName, templateId, "mapreduce", mrName);
    HttpResponse response = doGet(getVersionedAPIPath(logsUrl, "v3", namespaceId));
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    String out = EntityUtils.toString(response.getEntity());
    List<String> logLines = Lists.newArrayList(Splitter.on("\n").omitEmptyStrings().split(out));
    Assert.assertEquals(15, logLines.size());
    int expected = 20;
    for (String log : logLines) {
      String expectedStr = mrName + "&lt;img&gt;-" + expected;
      Assert.assertEquals(expectedStr, log.substring(log.length() - expectedStr.length()));
      expected++;
    }
  }

  private String getOffset(long offset) throws UnsupportedEncodingException {
    return URLEncoder.encode(GSON.toJson(new LogOffset(offset, offset)), "utf-8");
  }
}
