/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.automationanywhere.model.AutomationAnywhereHttpRequestBuilder;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.Map;

public record ApiKeyAuthProvider(
    String username, String apiKey, String controlRoomUrl, Integer connectionTimeoutInSeconds)
    implements AuthenticationProvider {

  private static final String USERNAME_KEY = "username";
  private static final String API_KEY = "apiKey";

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
    return Map.of(USERNAME_KEY, username, API_KEY, apiKey);
  }
}
