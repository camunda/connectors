/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A call of a runtime tool by the model.
 *
 * @param id the tool call id assigned by the model provider
 * @param name the name of the tool
 * @param arguments the arguments as the model sent them; a document parameter holds the id of its
 *     document, also when the model passed the {@code <doc />} tag of the document
 */
public record RuntimeToolCall(String id, String name, Map<String, Object> arguments) {

  public RuntimeToolCall {
    // a model can send an explicit null value, which an immutable Map.copyOf would reject
    arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
  }

  /**
   * Binds the {@link #arguments() arguments} to {@code type}; a document parameter is bound to a
   * {@code String}, the id of the document.
   *
   * @throws RuntimeToolException if the arguments do not match {@code type}
   */
  public <A> A argumentsAs(Class<A> type, ObjectMapper objectMapper) {
    try {
      return objectMapper.convertValue(arguments, type);
    } catch (IllegalArgumentException e) {
      throw new RuntimeToolException(
          "Invalid arguments for tool '%s': %s".formatted(name, rootMessage(e)), e);
    }
  }

  private static String rootMessage(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return String.valueOf(root.getMessage() == null ? error.getMessage() : root.getMessage());
  }
}
