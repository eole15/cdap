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

package co.cask.cdap.test;

import co.cask.cdap.api.metrics.RuntimeMetrics;

import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Managing the running Service in an application.
 */
public interface ServiceManager {
  /**
   * Changes the number of instances.
   *
   * @param instances Number of instances to change to.
   */
  void setInstances(int instances);

  /**
   * Returns the number of requested instances.
   */
  int getRequestedInstances();

  /**
   * Returns the number of provisioned instances.
   */
  int getProvisionedInstances();

  /**
   * Stops the running service.
   */
  void stop();

  /**
   * Checks if Service is Running
   */
  boolean isRunning();

  /**
   * Used to discover the Service managed by this ServiceManager.
   * @return URL of the Service or {@code null} if the service is not available
   */
  URL getServiceURL();


  /**
   * Used to discover the Service managed by this ServiceManager which allows a custom timeout
   * value to wait for the service to be available.
   *
   * @param timeout how long to wait before giving up, in unit of {@code timeoutUnit}
   * @param timeoutUnit a {@link TimeUnit} to interpret the value of {@code timeout}
   * @return URL of the service or {@code null} if the service is not available
   */
  URL getServiceURL(long timeout, TimeUnit timeoutUnit);

  /**
   * Wait for the status of the Service with 5 seconds timeout.
   * @param status true if waiting for started, false if waiting for stopped.
   * @throws InterruptedException if the method is interrupted while waiting for the status.
   */
  void waitForStatus(boolean status) throws InterruptedException;

  /**
   * Wait for the status of the Service, retrying a given number of times with a timeout between attempts.
   * @param status true if waiting for started, false if waiting for stopped.
   * @param retries number of attempts to check for status.
   * @param timeout timeout in seconds between attempts.
   * @throws InterruptedException if the method is interrupted while waiting for the status.
   */
  void waitForStatus(boolean status, int retries, int timeout) throws InterruptedException;

  /**
   * @return the Service metrics.
   */
  RuntimeMetrics getMetrics();
}
