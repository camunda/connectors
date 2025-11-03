/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.lang.Nullable;

public record McpRemoteClientRequest(@Valid @NotNull McpRemoteClientRequestData data) {
  public record McpRemoteClientRequestData(
      @Valid @NotNull HttpConnectionConfiguration connection,
      @Valid @Nullable McpClientToolsConfiguration tools,
      @Valid @NotNull McpClientOperationConfiguration operation) {

    public record HttpConnectionConfiguration(
        @TemplateProperty(
                group = "connection",
                label = "SSE URL",
                description =
                    "SSE URL to connect to the MCP server. Typically ends with <code>/sse</code>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            @NotBlank
            String sseUrl,
        @TemplateProperty(
                group = "connection",
                label = "HTTP headers",
                description = "Map of HTTP headers to add to the request.",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, String> headers,
        @TemplateProperty(
                group = "connection",
                description =
                    "Timeout for individual HTTP requests as ISO-8601 duration (example: <code>PT30S</code>)",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                optional = true)
            Duration timeout) {}
  }
}
