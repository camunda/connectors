/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.http.base.HttpService;

public record TokenBasedAuthProvider(String token) implements AuthenticationProvider {
  @Override
  public String obtainToken(final HttpService httpService, final ObjectMapper objectMapper) {
    return token;
  }
}
