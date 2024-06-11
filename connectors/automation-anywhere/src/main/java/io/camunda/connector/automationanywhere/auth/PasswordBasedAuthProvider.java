/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.automationanywhere.model.AutomationAnywhereHttpRequestBuilder;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.services.HttpService;
import java.util.Map;

public record PasswordBasedAuthProvider(
    String username,
    String password,
    Boolean multipleLogin,
    String controlRoomUrl,
    Integer connectionTimeoutInSeconds)
    implements AuthenticationProvider {

  private static final String USERNAME_KEY = "username";
  private static final String PASSWORD_KEY = "password";
  private static final String MULTIPLE_LOGIN_KEY = "multipleLogin";

  @Override
  public String obtainToken(final HttpService httpService, final ObjectMapper objectMapper) {

    final var request =
        new AutomationAnywhereHttpRequestBuilder()
            .withUrl(getAuthenticationRequestUrl(controlRoomUrl))
            .withBody(createRequestBody())
            .withMethod(HttpMethod.POST)
            .withTimeoutInSeconds(connectionTimeoutInSeconds)
            .build();

    final var result = httpService.executeConnectorRequest(request);
    return fetchToken(result, objectMapper);
  }

  private Map<String, Object> createRequestBody() {
    return Map.of(
        USERNAME_KEY, username, PASSWORD_KEY, password, MULTIPLE_LOGIN_KEY, multipleLogin);
  }
}
