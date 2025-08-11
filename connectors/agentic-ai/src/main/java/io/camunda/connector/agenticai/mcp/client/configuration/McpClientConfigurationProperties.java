/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.configuration.validation.ValidMcpClientConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai.mcp.client")
public record McpClientConfigurationProperties(
    @NotNull @DefaultValue("true") Boolean enabled,
    @NotNull @DefaultValue Map<@NotBlank String, @NotNull @Valid McpClientConfiguration> clients) {

  @ValidMcpClientConfiguration
  public record McpClientConfiguration(
      @DefaultValue("true") boolean enabled,
      StdioMcpClientTransportConfiguration stdio,
      SseHttpMcpClientTransportConfiguration sse,
      Duration initializationTimeout,
      Duration toolExecutionTimeout,
      Duration reconnectInterval) {}

  public sealed interface McpClientTransportConfiguration
      permits StdioMcpClientTransportConfiguration, SseHttpMcpClientTransportConfiguration {}

  public record StdioMcpClientTransportConfiguration(
      @NotBlank String command,
      @NotNull @DefaultValue List<String> args,
      @NotNull @DefaultValue Map<String, String> env,
      @DefaultValue("true") boolean logEvents)
      implements McpClientTransportConfiguration {}

  public record SseHttpMcpClientTransportConfiguration(
      @NotBlank String url,
      @NotNull @DefaultValue Map<String, String> headers, // TODO NOT SUPPORTED YET
      Duration timeout,
      @DefaultValue("false") boolean logRequests,
      @DefaultValue("false") boolean logResponses)
      implements McpClientTransportConfiguration {}
}
