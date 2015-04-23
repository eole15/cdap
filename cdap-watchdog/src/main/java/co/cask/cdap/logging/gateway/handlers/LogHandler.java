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

package co.cask.cdap.logging.gateway.handlers;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.gateway.auth.Authenticator;
import co.cask.cdap.gateway.handlers.AuthenticatedHttpHandler;
import co.cask.cdap.logging.LoggingConfiguration;
import co.cask.cdap.logging.context.LoggingContextHelper;
import co.cask.cdap.logging.filter.Filter;
import co.cask.cdap.logging.filter.FilterParser;
import co.cask.cdap.logging.read.LogOffset;
import co.cask.cdap.logging.read.LogReader;
import co.cask.cdap.logging.read.ReadRange;
import co.cask.cdap.proto.ProgramType;
import co.cask.http.HttpHandler;
import co.cask.http.HttpResponder;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * v3 {@link HttpHandler} to handle /logs requests
 */
@Singleton
@Path(Constants.Gateway.API_VERSION_3)
public class LogHandler extends AuthenticatedHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(LogHandler.class);

  private final LogReader logReader;
  private final String logPattern;

  @Inject
  public LogHandler(Authenticator authenticator, LogReader logReader, CConfiguration cConfig) {
    super(authenticator);
    this.logReader = logReader;
    this.logPattern = cConfig.get(LoggingConfiguration.LOG_PATTERN, LoggingConfiguration.DEFAULT_LOG_PATTERN);
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/{program-type}/{program-id}/logs")
  public void getLogs(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                      @PathParam("app-id") String appId, @PathParam("program-type") String programType,
                      @PathParam("program-id") String programId,
                      @QueryParam("start") @DefaultValue("-1") long fromTimeMsParam,
                      @QueryParam("stop") @DefaultValue(Long.MAX_VALUE + "") long toTimeMsParam,
                      @QueryParam("escape") @DefaultValue("true") boolean escape,
                      @QueryParam("filter") @DefaultValue("") String filterStr,
                      @QueryParam("adapterid") String adapterId) {
    LoggingContext loggingContext =
      LoggingContextHelper.getLoggingContext(namespaceId, appId, programId,
                                             getProgramType(ProgramType.valueOfCategoryName(programType)), adapterId);
    doGetLogs(responder, loggingContext, fromTimeMsParam, toTimeMsParam, escape, filterStr);
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/{program-type}/{program-id}/runs/{run-id}/logs")
  public void getRunIdLogs(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                           @PathParam("app-id") String appId, @PathParam("program-type") String programType,
                           @PathParam("program-id") String programId, @PathParam("run-id") String runId,
                           @QueryParam("start") @DefaultValue("-1") long fromTimeMsParam,
                           @QueryParam("stop") @DefaultValue(Long.MAX_VALUE + "") long toTimeMsParam,
                           @QueryParam("escape") @DefaultValue("true") boolean escape,
                           @QueryParam("filter") @DefaultValue("") String filterStr,
                           @QueryParam("adapterid") String adapterId) {
    LoggingContext loggingContext =
      LoggingContextHelper.getLoggingContextWithRunId(namespaceId, appId, programId,
                                                      getProgramType(ProgramType.valueOfCategoryName(programType)),
                                                      runId, adapterId);
    doGetLogs(responder, loggingContext, fromTimeMsParam, toTimeMsParam, escape, filterStr);
  }

  private void doGetLogs(HttpResponder responder, LoggingContext loggingContext,
                         long fromTimeMsParam, long toTimeMsParam, boolean escape, String filterStr) {
    try {
      Filter filter = FilterParser.parse(filterStr);
      long fromTimeMs = TimeUnit.MILLISECONDS.convert(fromTimeMsParam, TimeUnit.SECONDS);
      long toTimeMs = TimeUnit.MILLISECONDS.convert(toTimeMsParam, TimeUnit.SECONDS);

      if (fromTimeMs < 0 || toTimeMs < 0 || toTimeMs <= fromTimeMs) {
        responder.sendString(HttpResponseStatus.BAD_REQUEST, "Invalid time range. 'start' and 'stop' should be " +
          "greater than zero and 'stop' should be greater than 'start'.");
        return;
      }

      ChunkedLogReaderCallback logCallback = new ChunkedLogReaderCallback(responder, logPattern, escape);
      logReader.getLog(loggingContext, fromTimeMs, toTimeMs, filter, logCallback);
    } catch (SecurityException e) {
      responder.sendStatus(HttpResponseStatus.UNAUTHORIZED);
    } catch (IllegalArgumentException e) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    }  catch (Throwable e) {
      LOG.error("Caught exception", e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/{program-type}/{program-id}/logs/next")
  public void next(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                   @PathParam("app-id") String appId, @PathParam("program-type") String programType,
                   @PathParam("program-id") String programId, @QueryParam("max") @DefaultValue("50") int maxEvents,
                   @QueryParam("fromOffset") @DefaultValue("") String fromOffsetStr,
                   @QueryParam("escape") @DefaultValue("true") boolean escape,
                   @QueryParam("filter") @DefaultValue("") String filterStr,
                   @QueryParam("adapterid") String adapterId) {
    LoggingContext loggingContext =
      LoggingContextHelper.getLoggingContext(namespaceId, appId,
                                             programId, getProgramType(ProgramType.valueOfCategoryName(programType)),
                                             adapterId);
    doNext(responder, loggingContext, maxEvents, fromOffsetStr, escape, filterStr);
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/{program-type}/{program-id}/runs/{run-id}/logs/next")
  public void runIdNext(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                        @PathParam("app-id") String appId, @PathParam("program-type") String programType,
                        @PathParam("program-id") String programId, @PathParam("run-id") String runId,
                        @QueryParam("max") @DefaultValue("50") int maxEvents,
                        @QueryParam("fromOffset") @DefaultValue("") String fromOffsetStr,
                        @QueryParam("escape") @DefaultValue("true") boolean escape,
                        @QueryParam("filter") @DefaultValue("") String filterStr,
                        @QueryParam("adapterid") String adapterId) {
    LoggingContext loggingContext =
      LoggingContextHelper.getLoggingContextWithRunId(namespaceId, appId, programId,
                                                      getProgramType(ProgramType.valueOfCategoryName(programType)),
                                                      runId, adapterId);
    doNext(responder, loggingContext, maxEvents, fromOffsetStr, escape, filterStr);
  }

  private void doNext(HttpResponder responder, LoggingContext loggingContext, int maxEvents,
                      String fromOffsetStr, boolean escape, String filterStr) {
    try {
      Filter filter = FilterParser.parse(filterStr);

      LogReaderCallback logCallback = new LogReaderCallback(responder, logPattern, escape);

      LogOffset logOffset = FormattedLogEvent.parseLogOffset(fromOffsetStr);
      ReadRange readRange = ReadRange.createFromRange(logOffset);
      logReader.getLogNext(loggingContext, readRange, maxEvents, filter, logCallback);
    } catch (SecurityException e) {
      responder.sendStatus(HttpResponseStatus.UNAUTHORIZED);
    } catch (IllegalArgumentException e) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (Throwable e) {
      LOG.error("Caught exception", e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/{program-type}/{program-id}/logs/prev")
  public void prev(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                   @PathParam("app-id") String appId, @PathParam("program-type") String programType,
                   @PathParam("program-id") String programId, @QueryParam("max") @DefaultValue("50") int maxEvents,
                   @QueryParam("fromOffset") @DefaultValue("") String fromOffsetStr,
                   @QueryParam("escape") @DefaultValue("true") boolean escape,
                   @QueryParam("filter") @DefaultValue("") String filterStr,
                   @QueryParam("adapterid") String adapterId) {
    LoggingContext loggingContext =
      LoggingContextHelper.getLoggingContext(namespaceId, appId, programId,
                                             getProgramType(ProgramType.valueOfCategoryName(programType)), adapterId);
    doPrev(responder, loggingContext, maxEvents, fromOffsetStr, escape, filterStr);
  }

  @GET
  @Path("/namespaces/{namespace-id}/apps/{app-id}/{program-type}/{program-id}/runs/{run-id}/logs/prev")
  public void runIdPrev(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                        @PathParam("app-id") String appId, @PathParam("program-type") String programType,
                        @PathParam("program-id") String programId, @PathParam("run-id") String runId,
                        @QueryParam("max") @DefaultValue("50") int maxEvents,
                        @QueryParam("fromOffset") @DefaultValue("") String fromOffsetStr,
                        @QueryParam("escape") @DefaultValue("true") boolean escape,
                        @QueryParam("filter") @DefaultValue("") String filterStr,
                        @QueryParam("adapterid") String adapterId) {
    LoggingContext loggingContext =
      LoggingContextHelper.getLoggingContextWithRunId(namespaceId, appId, programId,
                                                      getProgramType(ProgramType.valueOfCategoryName(programType)),
                                                      runId, adapterId);
    doPrev(responder, loggingContext, maxEvents, fromOffsetStr, escape, filterStr);
  }

  private void doPrev(HttpResponder responder, LoggingContext loggingContext,
                      int maxEvents, String fromOffsetStr, boolean escape, String filterStr) {
    try {
      Filter filter = FilterParser.parse(filterStr);

      LogReaderCallback logCallback = new LogReaderCallback(responder, logPattern, escape);
      LogOffset logOffset = FormattedLogEvent.parseLogOffset(fromOffsetStr);
      ReadRange readRange = ReadRange.createToRange(logOffset);
      logReader.getLogPrev(loggingContext, readRange,
                           maxEvents, filter, logCallback);
    } catch (SecurityException e) {
      responder.sendStatus(HttpResponseStatus.UNAUTHORIZED);
    } catch (IllegalArgumentException e) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (Throwable e) {
      LOG.error("Caught exception", e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Path("/system/{component-id}/{service-id}/logs")
  public void sysList(HttpRequest request, HttpResponder responder, @PathParam("component-id") String componentId,
                      @PathParam("service-id") String serviceId,
                      @QueryParam("start") @DefaultValue("-1") long fromTimeMsParam,
                      @QueryParam("stop") @DefaultValue("-1") long toTimeMsParam,
                      @QueryParam("escape") @DefaultValue("true") boolean escape,
                      @QueryParam("filter") @DefaultValue("") String filterStr) {
    try {
      Filter filter = FilterParser.parse(filterStr);
      long fromTimeMs = TimeUnit.MILLISECONDS.convert(fromTimeMsParam, TimeUnit.SECONDS);
      long toTimeMs = TimeUnit.MILLISECONDS.convert(toTimeMsParam, TimeUnit.SECONDS);

      if (fromTimeMs < 0 || toTimeMs < 0 || toTimeMs <= fromTimeMs) {
        responder.sendString(HttpResponseStatus.BAD_REQUEST, "Invalid time range. 'start' and 'stop' should be " +
          "greater than zero and stop should be greater than start.");
        return;
      }

      LoggingContext loggingContext = LoggingContextHelper.getLoggingContext(Constants.SYSTEM_NAMESPACE, componentId,
                                                                             serviceId);
      ChunkedLogReaderCallback logCallback = new ChunkedLogReaderCallback(responder, logPattern, escape);
      logReader.getLog(loggingContext, fromTimeMs, toTimeMs, filter, logCallback);
    } catch (IllegalArgumentException e) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (Throwable e) {
      LOG.error("Caught exception", e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Path("/system/{component-id}/{service-id}/logs/next")
  public void sysNext(HttpRequest request, HttpResponder responder, @PathParam("component-id") String componentId,
                      @PathParam("service-id") String serviceId, @QueryParam("max") @DefaultValue("50") int maxEvents,
                      @QueryParam("fromOffset") @DefaultValue("") String fromOffsetStr,
                      @QueryParam("escape") @DefaultValue("true") boolean escape,
                      @QueryParam("filter") @DefaultValue("") String filterStr) {
    try {
      Filter filter = FilterParser.parse(filterStr);

      LoggingContext loggingContext = LoggingContextHelper.getLoggingContext(Constants.SYSTEM_NAMESPACE, componentId,
                                                                             serviceId);
      LogReaderCallback logCallback = new LogReaderCallback(responder, logPattern, escape);
      LogOffset logOffset = FormattedLogEvent.parseLogOffset(fromOffsetStr);
      ReadRange readRange = ReadRange.createFromRange(logOffset);
      logReader.getLogNext(loggingContext, readRange,
                           maxEvents, filter, logCallback);
    } catch (IllegalArgumentException e) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (Throwable e) {
      LOG.error("Caught exception", e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Path("/system/{component-id}/{service-id}/logs/prev")
  public void sysPrev(HttpRequest request, HttpResponder responder, @PathParam("component-id") String componentId,
                      @PathParam("service-id") String serviceId, @QueryParam("max") @DefaultValue("50") int maxEvents,
                      @QueryParam("fromOffset") @DefaultValue("") String fromOffsetStr,
                      @QueryParam("escape") @DefaultValue("true") boolean escape,
                      @QueryParam("filter") @DefaultValue("") String filterStr) {
    try {
      Filter filter = FilterParser.parse(filterStr);

      LoggingContext loggingContext = LoggingContextHelper.getLoggingContext(Constants.SYSTEM_NAMESPACE, componentId,
                                                                             serviceId);
      LogReaderCallback logCallback = new LogReaderCallback(responder, logPattern, escape);
      LogOffset logOffset = FormattedLogEvent.parseLogOffset(fromOffsetStr);
      ReadRange readRange = ReadRange.createToRange(logOffset);
      logReader.getLogPrev(loggingContext, readRange,
                           maxEvents, filter, logCallback);
    } catch (IllegalArgumentException e) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, e.getMessage());
    } catch (Throwable e) {
      LOG.error("Caught exception", e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private ProgramType getProgramType(ProgramType programType) {
    Preconditions.checkNotNull(programType, "ProgramType cannot be null");

    switch (programType) {
      case FLOW:
        return ProgramType.FLOW;
      case MAPREDUCE:
        return ProgramType.MAPREDUCE;
      case SPARK:
        return ProgramType.SPARK;
      case SERVICE:
        return ProgramType.SERVICE;
      case WORKER:
        return ProgramType.WORKER;
      default:
        throw new IllegalArgumentException(String.format("Illegal program type %s", programType));
    }
  }
}
