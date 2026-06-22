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
import io.camunda.connector.agenticai.sandbox.spi.ExecRequest;
import io.camunda.connector.agenticai.sandbox.spi.FileInfo;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the {@code fs_read} internal tool: reads a file from the sandbox filesystem and
 * returns its text content. Binary files and files over the read cap return a marker string (design
 * §7a) — raw bytes are never returned to the LLM.
 */
public class FsReadToolHandler implements InternalToolHandler {

  /** Default maximum size (in bytes) for a text file that will be returned inline. */
  static final long DEFAULT_MAX_READ_BYTES = ExecRequest.DEFAULT_MAX_OUTPUT_BYTES;

  private final long maxReadBytes;
  private final ToolDefinition definition;

  public FsReadToolHandler() {
    this(DEFAULT_MAX_READ_BYTES);
  }

  public FsReadToolHandler(long maxReadBytes) {
    this.maxReadBytes = maxReadBytes;
    this.definition = buildDefinition();
  }

  @Override
  public String name() {
    return InternalToolNames.FS_READ;
  }

  @Override
  public ToolDefinition definition() {
    return definition;
  }

  @Override
  public ToolCallResult execute(ToolCall toolCall, SandboxSession session) {
    String path = (String) toolCall.arguments().get("path");
    if (path == null || path.isBlank()) {
      return errorResult(toolCall, "Missing required argument: path");
    }

    FileInfo info;
    try {
      info = session.fs().stat(path);
    } catch (Exception e) {
      return errorResult(
          toolCall, e.getMessage() != null ? e.getMessage() : "stat failed: " + path);
    }

    // Binary file — never return raw bytes; point the LLM to export_document.
    if (info.isBinary()) {
      String marker =
          OutputBounds.binaryFileMarker(
              info.size(),
              info.contentType() != null ? info.contentType() : "application/octet-stream");
      return successResult(toolCall, marker);
    }

    // Oversized text file — return a marker with guidance.
    if (info.size() > maxReadBytes) {
      String marker =
          OutputBounds.oversizedFileMarker(
              info.size(), info.contentType() != null ? info.contentType() : "text/plain");
      return successResult(toolCall, marker);
    }

    byte[] bytes;
    try {
      bytes = session.fs().read(path);
    } catch (Exception e) {
      return errorResult(
          toolCall, e.getMessage() != null ? e.getMessage() : "read failed: " + path);
    }

    // Double-check for binary content (the SPI's stat may race with a write in theory; also guards
    // against providers where stat.isBinary() is imprecise).
    if (OutputBounds.isBinary(bytes)) {
      String marker =
          OutputBounds.binaryFileMarker(
              bytes.length,
              info.contentType() != null ? info.contentType() : "application/octet-stream");
      return successResult(toolCall, marker);
    }

    return successResult(toolCall, new String(bytes, StandardCharsets.UTF_8));
  }

  private static ToolCallResult successResult(ToolCall toolCall, String content) {
    return ToolCallResult.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content(content)
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
    pathProp.put(PROPERTY_DESCRIPTION, "Absolute path to the file to read.");

    Map<String, Object> properties = Map.of("path", pathProp);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("path"));

    return ToolDefinition.builder()
        .name(InternalToolNames.FS_READ)
        .description(
            "Read a file from the sandbox filesystem. "
                + "Returns the file's text content for UTF-8 text files. "
                + "Binary files or files over the size limit return a marker — use export_document to retrieve them as Camunda Documents.")
        .inputSchema(schema)
        .build();
  }
}
