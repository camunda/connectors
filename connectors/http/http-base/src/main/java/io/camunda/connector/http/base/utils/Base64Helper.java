/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.base.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Base64Helper {

  private Base64Helper() {}

  /**
   * Builds a basic authentication header value. The output is a string that can be used as the
   * value of the "Authorization" header. It is in the format "Basic base64(username:password)".
   *
   * @param username the username
   * @param password the password
   * @return the basic authentication header value in the format "Basic base64(username:password)"
   */
  public static String buildBasicAuthenticationHeader(String username, String password) {
    String passwordForHeader = password;
    // checking against "SPEC_PASSWORD_EMPTY_PATTERN" to prevent breaking change
    if (password == null || password.equals("SPEC_PASSWORD_EMPTY_PATTERN")) {
      passwordForHeader = "";
    }
    String headerValue = username + ":" + passwordForHeader;
    return "Basic "
        + Base64.getEncoder().encodeToString(headerValue.getBytes(StandardCharsets.UTF_8));
  }
}
