/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import io.camunda.connector.agenticai.autoconfigure.validation.ValidMcpClientConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
    @NotNull @DefaultValue Map<@NotBlank String, @NotNull @Valid McpClientConfiguration> clients) {

  @ValidMcpClientConfiguration
  public record McpClientConfiguration(
      @DefaultValue("true") boolean enabled,
      StdioMcpClientTransportConfiguration stio,
      HttpMcpClientTransportConfiguration http,
      Duration initializationTimeout,
      Duration toolExecutionTimeout,
      Duration reconnectInterval) {}

  public sealed interface McpClientTransportConfiguration
      permits StdioMcpClientTransportConfiguration, HttpMcpClientTransportConfiguration {}

  public record StdioMcpClientTransportConfiguration(
      @NotNull @NotEmpty List<String> command,
      @NotNull @DefaultValue Map<String, String> env,
      @DefaultValue("true") boolean logEvents)
      implements McpClientTransportConfiguration {}

  public record HttpMcpClientTransportConfiguration(
      @NotBlank String sseUrl,
      Duration timeout,
      @DefaultValue("true") boolean logRequests,
      @DefaultValue("true") boolean logResponses)
      implements McpClientTransportConfiguration {}
}
