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

package co.cask.cdap.common.logging.common;

/**
 * Defines log writer interface.
 */
public interface LogWriter {
  /**
   * Write a log message with a given log level.
   * @param tag   tag
   * @param level log level
   * @param message log message
   * @return  true on successful write
   */
  public boolean write(String tag, String level, String message);
}
