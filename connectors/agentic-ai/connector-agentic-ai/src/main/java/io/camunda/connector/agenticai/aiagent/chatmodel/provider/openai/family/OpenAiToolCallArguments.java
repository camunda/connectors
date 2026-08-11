/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Parses a tool call's {@code arguments} JSON string into the domain's untyped map shape. Shared
 * between the Completions and Responses response converters, which otherwise duplicated this
 * verbatim.
 *
 * <p>No blank/missing guard is needed: both API families' {@code arguments} accessors are {@code
 * getRequired("arguments")}, throwing if the field is absent, and OpenAI always sends a valid JSON
 * object string -- {@code "{}"} for a no-argument call -- never a blank or missing one.
 */
public final class OpenAiToolCallArguments {

  private OpenAiToolCallArguments() {}

  public static Map<String, Object> parse(ObjectMapper objectMapper, String argumentsJson) {
    try {
      final Map<String, Object> arguments =
          objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
      return arguments != null ? arguments : Map.of();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse tool call arguments", e);
    }
  }
}
