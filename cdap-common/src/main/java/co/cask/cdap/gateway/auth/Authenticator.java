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

package co.cask.cdap.gateway.auth;

import org.jboss.netty.handler.codec.http.HttpRequest;


/**
 * Interface that supports the authentication of requests.
 * <p/>
 * Underlying implementations can choose how they authenticate.  The current
 * implementation uses no authentication.
 */
public interface Authenticator {
  /**
   * Checks whether authentication is required or not.  If not, then no token
   * is required on any requests.
   *
   * @return true if authentication (and thus token) are required, false if not
   */
  public boolean isAuthenticationRequired();

  /**
   * Authenticates the specified HTTP request.
   *
   * @param httpRequest http request
   * @return true if authentication succeeds, false if not
   */
  public boolean authenticateRequest(HttpRequest httpRequest);

  // Note: we could actually have one of these instead of this API:
  // * return Account. But we don't want it as account has id as int, and we need String
  // * make authenticateRequest return accountId. But we don't want it as internally it would mean 2 requests to
  //   passport service, and in some situations accountId may not be needed.

  /**
   * Gets account for authenticated httpRequest.
   *
   * @param httpRequest http request
   * @return account
   */
  public String getAccountId(HttpRequest httpRequest);
}
