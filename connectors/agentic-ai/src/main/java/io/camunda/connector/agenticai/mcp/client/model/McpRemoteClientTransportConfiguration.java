/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.http.base.model.auth.Authentication;
import io.camunda.connector.http.base.model.auth.NoAuthentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value =
          McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration
              .class,
      name = "http"),
  @JsonSubTypes.Type(
      value =
          McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration.class,
      name = "sse")
})
@TemplateDiscriminatorProperty(
    label = "Type",
    group = "transport",
    name = "type",
    description = "Specify the MCP transport type.",
    defaultValue = "http")
public sealed interface McpRemoteClientTransportConfiguration
    permits McpRemoteClientTransportConfiguration
            .StreamableHttpMcpRemoteClientTransportConfiguration,
        McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration {

  interface McpRemoteClientConnection {
    Authentication authentication();

    String url();

    Map<String, String> headers();

    Duration timeout();
  }

  @TemplateSubType(id = "http", label = "Streamable HTTP")
  record StreamableHttpMcpRemoteClientTransportConfiguration(
      @Valid @NotNull StreamableHttpMcpRemoteClientConnection http)
      implements McpRemoteClientTransportConfiguration {
    public record StreamableHttpMcpRemoteClientConnection(
        @Valid Authentication authentication,
        @TemplateProperty(
                group = "transport",
                label = "URL",
                description =
                    "URL to connect to the MCP server. Typically ends with <code>/mcp</code>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            @NotBlank
            String url,
        @TemplateProperty(
                group = "transport",
                label = "HTTP headers",
                description = "Map of HTTP headers to add to the request.",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, String> headers,
        @TemplateProperty(
                group = "transport",
                description =
                    "Timeout for individual HTTP requests as ISO-8601 duration (example: <code>PT30S</code>)",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                optional = true)
            Duration timeout)
        implements McpRemoteClientConnection {

      public StreamableHttpMcpRemoteClientConnection {
        if (authentication == null) {
          authentication = new NoAuthentication();
        }
      }
    }
  }

  @TemplateSubType(id = "sse", label = "SSE")
  record SseHttpMcpRemoteClientTransportConfiguration(
      @Valid @NotNull SseHttpMcpRemoteClientConnection sse)
      implements McpRemoteClientTransportConfiguration {
    public record SseHttpMcpRemoteClientConnection(
        @Valid Authentication authentication,
        @TemplateProperty(
                group = "transport",
                label = "URL",
                description =
                    "URL to connect to the MCP server. Typically ends with <code>/sse</code>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            @NotBlank
            String url,
        @TemplateProperty(
                group = "transport",
                label = "HTTP headers",
                description = "Map of HTTP headers to add to the request.",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, String> headers,
        @TemplateProperty(
                group = "transport",
                description =
                    "Timeout for individual HTTP requests as ISO-8601 duration (example: <code>PT30S</code>)",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                optional = true)
            Duration timeout)
        implements McpRemoteClientConnection {

      public SseHttpMcpRemoteClientConnection {
        if (authentication == null) {
          authentication = new NoAuthentication();
        }
      }
    }
  }
}
