/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import io.camunda.connector.agenticai.sandbox.discovery.SandboxOperation;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/** Input record for the Sandbox (Daytona) outbound connector. */
public record SandboxDaytonaRequest(@Valid @NotNull SandboxDaytonaRequestData data) {

  /** Request payload nested under the {@code data} key. */
  public record SandboxDaytonaRequestData(
      @Valid @NotNull DaytonaConnection daytona,
      @TemplateProperty(
              group = "operation",
              label = "Operation",
              description = "The sandbox operation to perform.",
              defaultValue = "=toolCall.operation",
              feel = FeelMode.optional,
              optional = false)
          @NotNull
          SandboxOperation operation,
      @TemplateProperty(
              group = "operation",
              label = "Handle",
              description =
                  "The sandbox handle (returned by CREATE, used by BASH/FS_READ/FS_WRITE).",
              defaultValue = "=toolCall.handle",
              feel = FeelMode.optional,
              optional = true)
          @Nullable String handle,
      @TemplateProperty(
              group = "operation",
              label = "Command",
              description = "Shell command to run (BASH operation).",
              defaultValue = "=toolCall.command",
              feel = FeelMode.optional,
              optional = true)
          @Nullable String command,
      @TemplateProperty(
              group = "operation",
              label = "Path",
              description = "File path (FS_READ, FS_WRITE operations).",
              defaultValue = "=toolCall.path",
              feel = FeelMode.optional,
              optional = true)
          @Nullable String path,
      @TemplateProperty(
              group = "operation",
              label = "Content",
              description = "File content to write (FS_WRITE operation).",
              defaultValue = "=toolCall.content",
              feel = FeelMode.optional,
              optional = true)
          @Nullable String content,
      @TemplateProperty(
              group = "operation",
              label = "Document",
              description = "Document to import into the sandbox (IMPORT_DOCUMENT operation).",
              defaultValue = "=toolCall.document",
              feel = FeelMode.optional,
              optional = true)
          @Nullable Document document) {}
}
