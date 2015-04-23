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

package co.cask.cdap.logging.gateway.handlers;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import co.cask.cdap.logging.read.Callback;
import co.cask.cdap.logging.read.LogEvent;
import co.cask.http.HttpResponder;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringEscapeUtils;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Callback to handle log events from LogReader.
 */
class LogReaderCallback implements Callback {
  private final List<FormattedLogEvent> logResults;
  private final HttpResponder responder;
  private final PatternLayout patternLayout;
  private final boolean escape;
  private final AtomicInteger count = new AtomicInteger();

  LogReaderCallback(HttpResponder responder, String logPattern, boolean escape) {
    this.logResults = Lists.newArrayList();
    this.responder = responder;
    this.escape = escape;

    ch.qos.logback.classic.Logger rootLogger =
      (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    LoggerContext loggerContext = rootLogger.getLoggerContext();

    this.patternLayout = new PatternLayout();
    this.patternLayout.setContext(loggerContext);
    this.patternLayout.setPattern(logPattern);
  }

  @Override
  public void init() {
    patternLayout.start();
  }

  @Override
  public void handle(LogEvent event) {
    String log = patternLayout.doLayout(event.getLoggingEvent());
    log = escape ? StringEscapeUtils.escapeHtml(log) : log;

    logResults.add(new FormattedLogEvent(log, event.getOffset()));
    count.incrementAndGet();
  }

  @Override
  public int getCount() {
    return count.get();
  }

  @Override
  public void close() {
    patternLayout.stop();
    responder.sendJson(HttpResponseStatus.OK, logResults);
  }
}
