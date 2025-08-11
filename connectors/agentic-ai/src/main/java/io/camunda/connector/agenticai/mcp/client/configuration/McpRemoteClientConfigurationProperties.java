/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai.mcp.remote-client")
public record McpRemoteClientConfigurationProperties(
    @NotNull @DefaultValue("true") Boolean enabled, @NotNull @Valid ClientConfiguration client) {

  public McpRemoteClientConfigurationProperties {
    client = Optional.ofNullable(client).orElseGet(ClientConfiguration::defaultConfiguration);
  }

  public record ClientConfiguration(
      @DefaultValue("false") boolean logRequests,
      @DefaultValue("false") boolean logResponses,
      @NotNull @Valid ClientCacheConfiguration cache) {

    public static ClientConfiguration defaultConfiguration() {
      return new ClientConfiguration(false, false, ClientCacheConfiguration.defaultConfiguration());
    }

    public record ClientCacheConfiguration(
        @DefaultValue("true") boolean enabled,
        @NotNull @PositiveOrZero Long maximumSize,
        @NotNull Duration expireAfter) {

      public static final Long DEFAULT_MAXIMUM_SIZE = 15L;
      public static final Duration DEFAULT_EXPIRE_AFTER = Duration.ofMinutes(10);

      public ClientCacheConfiguration {
        maximumSize = Optional.ofNullable(maximumSize).orElse(DEFAULT_MAXIMUM_SIZE);
        expireAfter = Optional.ofNullable(expireAfter).orElse(DEFAULT_EXPIRE_AFTER);
      }

      public static ClientCacheConfiguration defaultConfiguration() {
        return new ClientCacheConfiguration(true, DEFAULT_MAXIMUM_SIZE, DEFAULT_EXPIRE_AFTER);
      }
    }
  }
}
