/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.lang.Nullable;

public record McpClientRemoteRequest(@Valid @NotNull McpClientRemoteRequestData data) {
  public record McpClientRemoteRequestData(
      @Valid @NotNull
          McpClientRemoteRequest.McpClientRemoteRequestData.HttpConnectionConfiguration connection,
      @Valid @Nullable McpClientToolsConfiguration tools,
      @Valid @NotNull McpClientOperationConfiguration operation) {

    public record HttpConnectionConfiguration(
        @TemplateProperty(
                group = "connection",
                label = "SSE URL",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            @NotBlank
            String sseUrl,
        @FEEL
            @TemplateProperty(
                group = "connection",
                label = "HTTP headers",
                description =
                    "Map of HTTP headers to add to the request <strong>(NOT SUPPORTED YET)</strong>.",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, String> headers,
        @TemplateProperty(optional = true) Duration timeout) {}
  }
}
