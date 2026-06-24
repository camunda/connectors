/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.sandbox.daytona.DaytonaClient.DaytonaSandboxInfo;
import io.camunda.connector.agenticai.sandbox.daytona.DaytonaClient.ExecOutcome;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxCreateResult;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolHandler;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.daytona.sdk.Daytona;
import io.daytona.sdk.Sandbox;
import java.nio.charset.StandardCharsets;
import java.util.List;

@OutboundConnector(
    name = "Sandbox (Daytona)",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:sandboxdaytona:1")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.sandbox.daytona.v0",
    name = "Sandbox (Daytona)",
    description = "Provisions and manages a Daytona sandbox for AI agent tool execution.",
    version = 1,
    category = @ElementTemplate.Category(id = "aiTools", name = "AI Tools"),
    inputDataClass = SandboxDaytonaRequest.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "sandbox", label = "Sandbox Configuration"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
    },
    extensionProperties = {
      @ElementTemplate.ExtensionProperty(
          name = GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION,
          value = SandboxGatewayToolHandler.GATEWAY_TYPE)
      // NO condition — AI agent tool mode ONLY
    })
public class SandboxDaytonaFunction implements OutboundConnectorFunction {

  private static final int EXEC_TIMEOUT_SECONDS = OutputBounds.DEFAULT_TIMEOUT_SECONDS;
  private static final long MAX_OUTPUT_BYTES = OutputBounds.DEFAULT_MAX_OUTPUT_BYTES;

  private final DaytonaClient daytonaClient;

  public SandboxDaytonaFunction() {
    this(new DaytonaClient());
  }

  public SandboxDaytonaFunction(DaytonaClient daytonaClient) {
    this.daytonaClient = daytonaClient;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final SandboxDaytonaRequest request = context.bindVariables(SandboxDaytonaRequest.class);
    final SandboxDaytonaRequest.SandboxDaytonaRequestData data = request.data();
    final DaytonaConnection config = data.daytona();
    final JobContext job = context.getJobContext();

    return switch (data.operation()) {
      case CREATE -> {
        Daytona daytona = DaytonaClient.buildClient(config.apiKey(), config.apiUrl());
        String processInstanceKey = String.valueOf(job.getProcessInstanceKey());
        String elementId = job.getElementId();
        DaytonaSandboxInfo info =
            daytonaClient.create(daytona, config, processInstanceKey, elementId);
        // TODO(P5): materialize skills from document storage + scan catalog
        yield new SandboxCreateResult(info.handle(), info.workDir(), List.of());
      }
      case BASH -> {
        String handle = requireHandle(data.handle(), "BASH");
        String command = requireArg(data.command(), "command", "BASH");
        Daytona daytona = DaytonaClient.buildClient(config.apiKey(), config.apiUrl());
        Sandbox sandbox = daytonaClient.connect(daytona, handle);
        ExecOutcome outcome = daytonaClient.exec(sandbox, command, EXEC_TIMEOUT_SECONDS);
        yield formatBashResult(outcome);
      }
      case FS_READ -> {
        String handle = requireHandle(data.handle(), "FS_READ");
        String path = requireArg(data.path(), "path", "FS_READ");
        Daytona daytona = DaytonaClient.buildClient(config.apiKey(), config.apiUrl());
        Sandbox sandbox = daytonaClient.connect(daytona, handle);
        byte[] bytes = daytonaClient.fsRead(sandbox, path);
        if (OutputBounds.isBinary(bytes)) {
          yield OutputBounds.binaryFileMarker(bytes.length, "application/octet-stream");
        }
        if (bytes.length > MAX_OUTPUT_BYTES) {
          yield OutputBounds.oversizedFileMarker(bytes.length, "text/plain");
        }
        yield new String(bytes, StandardCharsets.UTF_8);
      }
      case FS_WRITE -> {
        String handle = requireHandle(data.handle(), "FS_WRITE");
        String path = requireArg(data.path(), "path", "FS_WRITE");
        String content = data.content() != null ? data.content() : "";
        Daytona daytona = DaytonaClient.buildClient(config.apiKey(), config.apiUrl());
        Sandbox sandbox = daytonaClient.connect(daytona, handle);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        daytonaClient.fsWrite(sandbox, path, bytes);
        yield "Written " + bytes.length + " bytes to " + path;
      }
      case EXPORT_DOCUMENT ->
          throw new ConnectorException(
              "SANDBOX_NOT_IMPLEMENTED",
              // TODO(P4): implement EXPORT_DOCUMENT
              "EXPORT_DOCUMENT is not yet implemented (P4)");
      case IMPORT_DOCUMENT ->
          throw new ConnectorException(
              "SANDBOX_NOT_IMPLEMENTED",
              // TODO(P4): implement IMPORT_DOCUMENT
              "IMPORT_DOCUMENT is not yet implemented (P4)");
    };
  }

  private String formatBashResult(ExecOutcome outcome) {
    String stdout = renderOutput(outcome.stdout());
    String stderr = renderOutput(outcome.stderr());

    StringBuilder sb = new StringBuilder();
    sb.append("exit_code: ").append(outcome.exitCode()).append("\n");
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

  private String renderOutput(String text) {
    if (text == null || text.isEmpty()) return "";
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    if (OutputBounds.isBinary(bytes)) return OutputBounds.binaryOutputMarker(bytes.length);
    return OutputBounds.truncate(text, MAX_OUTPUT_BYTES, false);
  }

  private static String requireHandle(String handle, String op) {
    if (handle == null || handle.isBlank()) {
      throw new ConnectorException(
          "SANDBOX_MISSING_HANDLE", "handle is required for operation " + op);
    }
    return handle;
  }

  private static String requireArg(String value, String argName, String op) {
    if (value == null || value.isBlank()) {
      throw new ConnectorException(
          "SANDBOX_MISSING_ARG", argName + " is required for operation " + op);
    }
    return value;
  }
}
