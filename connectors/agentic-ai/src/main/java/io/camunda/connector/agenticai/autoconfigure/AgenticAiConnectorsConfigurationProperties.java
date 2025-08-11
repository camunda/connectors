/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai")
public record AgenticAiConnectorsConfigurationProperties(
    @Valid ToolsSchemaConfiguration tools) {

  public AgenticAiConnectorsConfigurationProperties {
    tools = Optional.ofNullable(tools).orElseGet(ToolsSchemaConfiguration::defaultConfiguration);
  }

  public record ToolsSchemaConfiguration(@Valid CacheConfiguration cache) {
    public static ToolsSchemaConfiguration defaultConfiguration() {
      return new ToolsSchemaConfiguration(CacheConfiguration.defaultConfiguration());
    }

    public record CacheConfiguration(
        boolean enabled, @PositiveOrZero Long maximumSize, Duration expireAfterWrite) {
      public static CacheConfiguration defaultConfiguration() {
        return new CacheConfiguration(true, 100L, Duration.ofMinutes(10));
      }
    }
  }
}
