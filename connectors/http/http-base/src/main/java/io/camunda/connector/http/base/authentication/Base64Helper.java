/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.authentication;

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
