/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.configuration.validation.ValidMcpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.auth.Authentication;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai.mcp.client")
public record McpClientConfigurationProperties(
    @NotNull @DefaultValue("true") Boolean enabled,
    @NotNull @DefaultValue Map<@NotBlank String, @NotNull @Valid McpClientConfiguration> clients) {

  @AgenticAiRecord
  @ValidMcpClientConfiguration
  public record McpClientConfiguration(
      @DefaultValue("true") boolean enabled,
      @Nullable StdioMcpClientTransportConfiguration stdio,
      @Nullable StreamableHttpMcpClientTransportConfiguration http,
      @Nullable SseHttpMcpClientTransportConfiguration sse,
      @Nullable Duration initializationTimeout,
      @Nullable Duration toolExecutionTimeout,
      @Nullable Duration reconnectInterval)
      implements McpClientConfigurationPropertiesMcpClientConfigurationBuilder.With {

    public static McpClientConfigurationPropertiesMcpClientConfigurationBuilder builder() {
      return McpClientConfigurationPropertiesMcpClientConfigurationBuilder.builder();
    }
  }

  public sealed interface McpClientTransportConfiguration
      permits StdioMcpClientTransportConfiguration,
          StreamableHttpMcpClientTransportConfiguration,
          SseHttpMcpClientTransportConfiguration {
    String type();
  }

  public record StdioMcpClientTransportConfiguration(
      @NotBlank String command,
      @NotNull @DefaultValue List<String> args,
      @NotNull @DefaultValue Map<String, String> env,
      @DefaultValue("true") boolean logEvents)
      implements McpClientTransportConfiguration {

    @Override
    public String type() {
      return "stdio";
    }
  }

  public record StreamableHttpMcpClientTransportConfiguration(
      @NotBlank String url,
      @NotNull @DefaultValue Map<String, String> headers,
      @Valid Authentication authentication,
      @PositiveOrZero Duration timeout,
      @DefaultValue("false") boolean logRequests,
      @DefaultValue("false") boolean logResponses)
      implements McpClientTransportConfiguration {

    @Override
    public String type() {
      return "http";
    }
  }

  public record SseHttpMcpClientTransportConfiguration(
      @NotBlank String url,
      @NotNull @DefaultValue Map<String, String> headers,
      @Valid Authentication authentication,
      @PositiveOrZero Duration timeout,
      @DefaultValue("false") boolean logRequests,
      @DefaultValue("false") boolean logResponses)
      implements McpClientTransportConfiguration {

    @Override
    public String type() {
      return "sse";
    }
  }
}
