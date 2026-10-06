/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A call of a local tool by the model.
 *
 * @param id the tool call id assigned by the model provider
 * @param name the name of the tool
 * @param arguments the arguments as the model sent them; document parameters hold the document ids
 * @param resolvedArguments the arguments with every document parameter the tool declares replaced
 *     by the document reference of the registry; equal to {@code arguments} for a tool without
 *     document parameters
 */
public record LocalToolCall(
    String id, String name, Map<String, Object> arguments, Map<String, Object> resolvedArguments) {

  public LocalToolCall {
    // a model can send an explicit null value, which an immutable Map.copyOf would reject
    arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    resolvedArguments = Collections.unmodifiableMap(new LinkedHashMap<>(resolvedArguments));
  }

  /**
   * Binds the {@link #resolvedArguments() resolved arguments} to {@code type}; a parameter of type
   * {@code Document} receives the document of the registry.
   *
   * @throws LocalToolException if the arguments do not match {@code type}
   */
  public <A> A argumentsAs(Class<A> type, ObjectMapper objectMapper) {
    try {
      return objectMapper.convertValue(resolvedArguments, type);
    } catch (IllegalArgumentException e) {
      throw new LocalToolException(
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
