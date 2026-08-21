/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record OpenAiRequestCustomizations(
    Map<String, String> headers,
    Map<String, String> queryParameters,
    Map<String, Object> bodyProperties) {

  public OpenAiRequestCustomizations(
      @Nullable Map<String, String> headers,
      @Nullable Map<String, String> queryParameters,
      @Nullable Map<String, Object> bodyProperties) {
    this.headers = Objects.requireNonNullElse(headers, Map.of());
    this.queryParameters = Objects.requireNonNullElse(queryParameters, Map.of());
    this.bodyProperties = Objects.requireNonNullElse(bodyProperties, Map.of());
  }
}
