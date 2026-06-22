/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the {@code fs_write} internal tool: writes UTF-8 text content to a file in the sandbox
 * filesystem. Parent-directory creation is delegated to the filesystem implementation.
 */
public class FsWriteToolHandler implements InternalToolHandler {

  private final ToolDefinition definition;

  public FsWriteToolHandler() {
    this.definition = buildDefinition();
  }

  @Override
  public String name() {
    return InternalToolNames.FS_WRITE;
  }

  @Override
  public ToolDefinition definition() {
    return definition;
  }

  @Override
  public ToolCallResult execute(
      ToolCall toolCall, SandboxSession session, InternalToolContext context) {
    String path = (String) toolCall.arguments().get("path");
    String content = (String) toolCall.arguments().get("content");

    if (path == null || path.isBlank()) {
      return errorResult(toolCall, "Missing required argument: path");
    }
    if (content == null) {
      return errorResult(toolCall, "Missing required argument: content");
    }

    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    try {
      session.fs().write(path, bytes);
    } catch (Exception e) {
      return errorResult(
          toolCall, e.getMessage() != null ? e.getMessage() : "write failed: " + path);
    }

    String result = "Written " + bytes.length + " bytes to " + path;
    return ToolCallResult.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content(result)
        .properties(
            Map.of(
                InternalToolExecutor.PROPERTY_EXECUTED_BY,
                InternalToolExecutor.EXECUTED_BY_SANDBOX))
        .build();
  }

  private static ToolCallResult errorResult(ToolCall toolCall, String message) {
    return ToolCallResult.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content("Error: " + message)
        .properties(
            Map.of(
                InternalToolExecutor.PROPERTY_EXECUTED_BY,
                InternalToolExecutor.EXECUTED_BY_SANDBOX))
        .build();
  }

  private static ToolDefinition buildDefinition() {
    Map<String, Object> pathProp = new LinkedHashMap<>();
    pathProp.put(PROPERTY_TYPE, TYPE_STRING);
    pathProp.put(PROPERTY_DESCRIPTION, "Absolute path to write the file to.");

    Map<String, Object> contentProp = new LinkedHashMap<>();
    contentProp.put(PROPERTY_TYPE, TYPE_STRING);
    contentProp.put(PROPERTY_DESCRIPTION, "UTF-8 text content to write.");

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", pathProp);
    properties.put("content", contentProp);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("path", "content"));

    return ToolDefinition.builder()
        .name(InternalToolNames.FS_WRITE)
        .description(
            "Write UTF-8 text content to a file in the sandbox filesystem. "
                + "Creates or overwrites the file at the given path. "
                + "Parent directory creation is handled by the filesystem provider.")
        .inputSchema(schema)
        .build();
  }
}
