/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.connector.agenticai.localtoolbox.LocalToolboxErrorCodes;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The operation requested of a {@code LocalToolboxClientFunction} activation, mirroring the MCP
 * client's {@code McpClientOperation} shape: a method discriminator plus a parameter map. The
 * gateway tool handler rewrites the operation at runtime (tool discovery vs. a specific tool call)
 * before the element is activated; the element template's default values simply read it back from
 * the injected {@code toolCall} variable.
 */
public sealed interface LocalToolboxOperation
    permits LocalToolboxOperation.LocalToolboxOperationImpl {

  LocalToolboxMethod method();

  Map<String, Object> params();

  static LocalToolboxOperation of(String method) {
    return of(method, Collections.emptyMap());
  }

  static LocalToolboxOperation of(String method, Map<String, Object> params) {
    return new LocalToolboxOperationImpl(LocalToolboxMethod.valueFrom(method), params);
  }

  enum LocalToolboxMethod {
    LIST_TOOLS("tools/list"),
    CALL_TOOL("tools/call");

    private static String supportedMethods() {
      return Stream.of(LocalToolboxMethod.values())
          .map(op -> op.methodName)
          .collect(Collectors.joining("', '"));
    }

    @JsonCreator
    public static LocalToolboxMethod valueFrom(String rawMethod) {
      for (LocalToolboxMethod method : values()) {
        if (method.methodName.equals(rawMethod)) {
          return method;
        }
      }
      throw new ConnectorException(
          LocalToolboxErrorCodes.ERROR_CODE_INVALID_METHOD,
          "Unsupported local toolbox method '%s'. Supported methods: '%s'"
              .formatted(rawMethod, supportedMethods()));
    }

    @JsonValue private final String methodName;

    LocalToolboxMethod(String methodName) {
      this.methodName = methodName;
    }

    public String methodName() {
      return methodName;
    }
  }

  record LocalToolboxOperationImpl(
      LocalToolboxMethod method,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> params)
      implements LocalToolboxOperation {}
}
