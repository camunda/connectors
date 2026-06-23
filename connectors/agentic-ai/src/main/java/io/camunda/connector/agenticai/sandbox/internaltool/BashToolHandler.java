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
import io.camunda.connector.agenticai.sandbox.spi.ExecResult;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the {@code bash} internal tool: runs a shell command via {@code bash -lc} in the
 * sandbox session and returns stdout/stderr/exitCode as a readable string block. Design §7a:
 * stdout/stderr are truncated to {@code maxOutputBytes}; binary stdout is replaced with a marker.
 */
public class BashToolHandler implements InternalToolHandler {

  static final int DEFAULT_TIMEOUT_SECONDS = ExecRequest.DEFAULT_TIMEOUT_SECONDS;
  static final long DEFAULT_MAX_OUTPUT_BYTES = ExecRequest.DEFAULT_MAX_OUTPUT_BYTES;

  private final int timeoutSeconds;
  private final long maxOutputBytes;
  private final ToolDefinition definition;

  public BashToolHandler() {
    this(DEFAULT_TIMEOUT_SECONDS, DEFAULT_MAX_OUTPUT_BYTES);
  }

  public BashToolHandler(int timeoutSeconds, long maxOutputBytes) {
    this.timeoutSeconds = timeoutSeconds;
    this.maxOutputBytes = maxOutputBytes;
    this.definition = buildDefinition();
  }

  @Override
  public String name() {
    return InternalToolNames.BASH;
  }

  @Override
  public ToolDefinition definition() {
    return definition;
  }

  @Override
  public ToolCallResult execute(
      ToolCall toolCall, SandboxSession session, InternalToolContext context) {
    String command = (String) toolCall.arguments().get("command");
    if (command == null || command.isBlank()) {
      return errorResult(toolCall, "Missing required argument: command");
    }

    ExecRequest req = new ExecRequest(command, null, null, timeoutSeconds, maxOutputBytes);
    ExecResult result = session.exec(req);

    String stdout = renderOutput(result.stdout(), result.truncated());
    String stderr = renderOutput(result.stderr(), false);

    String content = formatResult(result.exitCode(), stdout, stderr);
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

  /**
   * Renders a stdout/stderr string: if the raw bytes look binary, replace with a marker; otherwise
   * truncate to cap.
   */
  private String renderOutput(String text, boolean alreadyTruncated) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    if (OutputBounds.isBinary(bytes)) {
      return OutputBounds.binaryOutputMarker(bytes.length);
    }
    return OutputBounds.truncate(text, maxOutputBytes, alreadyTruncated);
  }

  private static String formatResult(int exitCode, String stdout, String stderr) {
    StringBuilder sb = new StringBuilder();
    sb.append("exit_code: ").append(exitCode).append("\n");
    if (!stdout.isEmpty()) {
      sb.append("stdout:\n").append(stdout);
      if (!stdout.endsWith("\n")) sb.append("\n");
    }
    if (!stderr.isEmpty()) {
      sb.append("stderr:\n").append(stderr);
      if (!stderr.endsWith("\n")) sb.append("\n");
    }
    return sb.toString().trim();
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
    Map<String, Object> commandProp = new LinkedHashMap<>();
    commandProp.put(PROPERTY_TYPE, TYPE_STRING);
    commandProp.put(
        PROPERTY_DESCRIPTION,
        "Shell command to run via bash -lc. Supports pipes, redirects, globs.");

    Map<String, Object> properties = Map.of("command", commandProp);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("command"));

    return ToolDefinition.builder()
        .name(InternalToolNames.BASH)
        .description(
            "Run a shell command in the sandbox via bash -lc. "
                + "Returns stdout, stderr, and exit code. "
                + "Stateless per call — workspace filesystem persists but shell state (cwd, env vars, background jobs) does not.")
        .inputSchema(schema)
        .metadata(Map.of(ToolDefinition.METADATA_SANDBOX_TOOL, true))
        .build();
  }
}
