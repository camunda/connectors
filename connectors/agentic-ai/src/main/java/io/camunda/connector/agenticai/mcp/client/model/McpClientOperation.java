/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import com.fasterxml.jackson.annotation.*;
import io.camunda.connector.agenticai.mcp.McpClientErrorCodes;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public sealed interface McpClientOperation permits McpClientOperation.McpClientOperationImpl {

  McpMethod method();

  Map<String, Object> params();

  static McpClientOperation of(String method) {
    return of(method, Collections.emptyMap());
  }

  static McpClientOperation of(String method, Map<String, Object> params) {
    McpMethod operation = McpMethod.valueFrom(method);
    return new McpClientOperationImpl(operation, params);
  }

  enum McpMethod {
    LIST_TOOLS("tools/list"),
    CALL_TOOL("tools/call"),
    LIST_RESOURCES("resources/list"),
    LIST_RESOURCE_TEMPLATES("resources/templates/list"),
    READ_RESOURCE("resources/read"),
    LIST_PROMPTS("prompts/list"),
    GET_PROMPT("prompts/get");

    private static String supportedMethods() {
      return Stream.of(
              LIST_TOOLS,
              CALL_TOOL,
              LIST_RESOURCES,
              LIST_RESOURCE_TEMPLATES,
              LIST_PROMPTS,
              GET_PROMPT)
          .map(op -> op.methodName)
          .collect(Collectors.joining("', '"));
    }

    @JsonCreator
    public static McpMethod valueFrom(String rawMethod) {
      for (McpMethod method : values()) {
        if (method.methodName.equals(rawMethod)) {
          return method;
        }
      }
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_METHOD,
          String.format(
              "Unsupported MCP method '%s'. Supported operations: '%s'",
              rawMethod, supportedMethods()));
    }

    @JsonValue private final String methodName;

    McpMethod(String methodName) {
      this.methodName = methodName;
    }

    public String methodName() {
      return methodName;
    }
  }

  record McpClientOperationImpl(
      McpMethod method, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> params)
      implements McpClientOperation {}
}
