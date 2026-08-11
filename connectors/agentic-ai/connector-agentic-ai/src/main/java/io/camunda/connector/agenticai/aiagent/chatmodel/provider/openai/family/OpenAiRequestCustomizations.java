/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family;

import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The resolved headers/queryParameters/bodyProperties for the currently selected backend
 * (openai-api or custom), normalized to non-null maps. Shared between the Completions and Responses
 * request converters (Task 5/8) so the per-backend switch isn't duplicated; mirrors {@code
 * AnthropicMessageRequestConverter}'s {@code RequestCustomizations}.
 */
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

  public static OpenAiRequestCustomizations from(OpenAiConnection connection) {
    return switch (connection.backend()) {
      case OpenAiApiBackend apiBackend ->
          new OpenAiRequestCustomizations(
              apiBackend.openai().headers(),
              apiBackend.openai().queryParameters(),
              apiBackend.openai().bodyProperties());
      case OpenAiCustomBackend custom ->
          new OpenAiRequestCustomizations(
              custom.custom().headers(),
              custom.custom().queryParameters(),
              custom.custom().bodyProperties());
    };
  }
}
