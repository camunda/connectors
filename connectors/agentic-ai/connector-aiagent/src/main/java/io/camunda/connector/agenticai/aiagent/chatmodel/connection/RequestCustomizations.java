/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.connection;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Additional headers, query parameters and body properties to send with every model call, empty if
 * none are configured.
 */
public record RequestCustomizations(
    Map<String, String> headers,
    Map<String, String> queryParameters,
    Map<String, Object> bodyProperties) {

  public static final RequestCustomizations NONE =
      new RequestCustomizations(Map.of(), Map.of(), Map.of());

  public static RequestCustomizations of(
      @Nullable Map<String, String> headers,
      @Nullable Map<String, String> queryParameters,
      @Nullable Map<String, Object> bodyProperties) {
    return new RequestCustomizations(
        Objects.requireNonNullElse(headers, Map.of()),
        Objects.requireNonNullElse(queryParameters, Map.of()),
        Objects.requireNonNullElse(bodyProperties, Map.of()));
  }
}
