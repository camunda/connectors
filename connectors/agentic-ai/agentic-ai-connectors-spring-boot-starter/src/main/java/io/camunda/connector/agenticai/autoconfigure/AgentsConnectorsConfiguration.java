/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda.connector.agents")
public record AgentsConnectorsConfiguration(ToolsSchemaConfiguration tools) {
  public AgentsConnectorsConfiguration(ToolsSchemaConfiguration tools) {
    this.tools =
        Optional.ofNullable(tools).orElseGet(ToolsSchemaConfiguration::defaultConfiguration);
  }

  public record ToolsSchemaConfiguration(CacheConfiguration cache) {
    public static ToolsSchemaConfiguration defaultConfiguration() {
      return new ToolsSchemaConfiguration(CacheConfiguration.defaultConfiguration());
    }

    public record CacheConfiguration(boolean enabled, int maxSize, Duration expireAfterWrite) {
      public static CacheConfiguration defaultConfiguration() {
        return new CacheConfiguration(true, 100, Duration.ofMinutes(5));
      }
    }
  }
}
