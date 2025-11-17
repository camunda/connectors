/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.configuration.validation.ValidMcpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.auth.Authentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.BasicAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.NoAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
      @NotNull(message = "MCP client type must not be null") McpClientType type,
      @Nullable StdioMcpClientTransportConfiguration stdio,
      @Nullable StreamableHttpMcpClientTransportConfiguration http,
      @Nullable SseHttpMcpClientTransportConfiguration sse,
      @Nullable Duration initializationTimeout,
      @Nullable Duration toolExecutionTimeout,
      @Nullable Duration reconnectInterval)
      implements McpClientConfigurationPropertiesMcpClientConfigurationBuilder.With {

    public enum McpClientType {
      STDIO(McpClientConfiguration::stdio),
      HTTP(McpClientConfiguration::http),
      SSE(McpClientConfiguration::sse);

      private final Function<McpClientConfiguration, McpClientTransportConfiguration>
          transportSupplier;

      McpClientType(
          Function<McpClientConfiguration, McpClientTransportConfiguration> transportSupplier) {
        this.transportSupplier = transportSupplier;
      }

      public McpClientTransportConfiguration getTransport(McpClientConfiguration config) {
        return transportSupplier.apply(config);
      }
    }

    public static McpClientConfigurationPropertiesMcpClientConfigurationBuilder builder() {
      return McpClientConfigurationPropertiesMcpClientConfigurationBuilder.builder();
    }

    public McpClientTransportConfiguration transport() {
      return type.getTransport(this);
    }
  }

  @AgenticAiRecord
  public record AuthenticationConfiguration(
      @NotNull @DefaultValue("NONE") AuthenticationType type,
      @Valid @Nullable BasicAuthentication basic,
      @Valid @Nullable BearerAuthentication bearer,
      @Valid @Nullable OAuthAuthentication oauth) {

    public enum AuthenticationType {
      NONE(config -> new NoAuthentication()),
      BASIC(AuthenticationConfiguration::basic),
      BEARER(AuthenticationConfiguration::bearer),
      OAUTH(AuthenticationConfiguration::oauth);

      private final Function<AuthenticationConfiguration, Authentication> authenticationSupplier;

      AuthenticationType(
          Function<AuthenticationConfiguration, Authentication> authenticationSupplier) {
        this.authenticationSupplier = authenticationSupplier;
      }

      public Authentication getAuthentication(AuthenticationConfiguration config) {
        return authenticationSupplier.apply(config);
      }
    }

    public static McpClientConfigurationPropertiesAuthenticationConfigurationBuilder builder() {
      return McpClientConfigurationPropertiesAuthenticationConfigurationBuilder.builder();
    }

    public Authentication authentication() {
      return type.getAuthentication(this);
    }
  }

  public sealed interface McpClientTransportConfiguration
      permits StdioMcpClientTransportConfiguration,
          StreamableHttpMcpClientTransportConfiguration,
          SseHttpMcpClientTransportConfiguration {}

  public record StdioMcpClientTransportConfiguration(
      @NotBlank String command,
      @NotNull @DefaultValue List<String> args,
      @NotNull @DefaultValue Map<String, String> env,
      @DefaultValue("true") boolean logEvents)
      implements McpClientTransportConfiguration {}

  public record StreamableHttpMcpClientTransportConfiguration(
      @NotBlank String url,
      @NotNull @DefaultValue Map<String, String> headers,
      @Valid @NotNull @DefaultValue AuthenticationConfiguration authentication,
      @PositiveOrZero Duration timeout,
      @DefaultValue("false") boolean logRequests,
      @DefaultValue("false") boolean logResponses)
      implements McpClientTransportConfiguration {}

  public record SseHttpMcpClientTransportConfiguration(
      @NotBlank String url,
      @NotNull @DefaultValue Map<String, String> headers,
      @Valid @NotNull @DefaultValue AuthenticationConfiguration authentication,
      @PositiveOrZero Duration timeout,
      @DefaultValue("false") boolean logRequests,
      @DefaultValue("false") boolean logResponses)
      implements McpClientTransportConfiguration {}
}
