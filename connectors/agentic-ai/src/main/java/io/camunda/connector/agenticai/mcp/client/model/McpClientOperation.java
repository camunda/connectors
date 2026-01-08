/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import com.fasterxml.jackson.annotation.*;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public sealed interface McpClientOperation permits McpClientOperation.McpClientOperationImpl {

  Operation method();

  Map<String, Object> parameters();

  static McpClientOperationImpl of(String method) {
    return of(method, Collections.emptyMap());
  }

  static McpClientOperationImpl of(String method, Map<String, Object> parameters) {
    Operation operation = Operation.valueFrom(method);
    return new McpClientOperationImpl(operation, parameters);
  }

  record McpClientOperationImpl(
      @JsonIgnore Operation operation,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonProperty("params")
          Map<String, Object> parameters)
      implements McpClientOperation {

    @Override
    @JsonGetter("method")
    public Operation method() {
      return operation;
    }
  }

  enum Operation {
    LIST_TOOLS("tools/list"),
    CALL_TOOL("tools/call"),
    LIST_RESOURCES("resources/list"),
    LIST_RESOURCE_TEMPLATES("resources/templates/list"),
    READ_RESOURCE("resources/read"),
    LIST_PROMPTS("prompts/list"),
    GET_PROMPT("prompts/get");

    public static String supportedOperations() {
      return Stream.of(LIST_TOOLS, CALL_TOOL, LIST_RESOURCES, LIST_RESOURCE_TEMPLATES)
          .map(op -> op.methodName)
          .collect(Collectors.joining("', '"));
    }

    @JsonCreator
    public static Operation valueFrom(String method) {
      for (Operation operation : values()) {
        if (operation.methodName.equals(method)) {
          return operation;
        }
      }
      throw new ConnectorException(
          "MCP_CLIENT_UNSUPPORTED_OPERATION",
          String.format(
              "Unsupported MCP operation '%s'. Supported operations: '%s'",
              method, String.join("', '", supportedOperations())));
    }

    @JsonValue private final String methodName;

    Operation(String methodName) {
      this.methodName = methodName;
    }

    public String methodName() {
      return methodName;
    }
  }
}
