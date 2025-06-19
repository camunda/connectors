/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = McpClientOperation.McpClientListToolsOperation.class,
      name = McpClientOperation.McpClientListToolsOperation.METHOD),
  @JsonSubTypes.Type(
      value = McpClientOperation.McpClientCallToolOperation.class,
      name = McpClientOperation.McpClientCallToolOperation.METHOD)
})
public sealed interface McpClientOperation
    permits McpClientOperation.McpClientListToolsOperation,
        McpClientOperation.McpClientCallToolOperation {
  String method();

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record McpClientListToolsOperation() implements McpClientOperation {
    public static final String METHOD = "tools/list";

    @Override
    public String method() {
      return METHOD;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record McpClientCallToolOperation(McpClientCallToolOperationParams params)
      implements McpClientOperation {
    public static final String METHOD = "tools/call";

    @Override
    public String method() {
      return METHOD;
    }

    public static McpClientCallToolOperation create(String name, Map<String, Object> arguments) {
      return new McpClientCallToolOperation(new McpClientCallToolOperationParams(name, arguments));
    }

    public record McpClientCallToolOperationParams(String name, Map<String, Object> arguments) {}
  }
}
