/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai")
public record AgenticAiConnectorsConfigurationProperties(
    @Valid @NestedConfigurationProperty ToolsProperties tools,
    @Valid @NestedConfigurationProperty AiAgentProperties aiagent) {

  public record AiAgentProperties(
      @Valid @NestedConfigurationProperty ChatModelProperties chatModel) {}

  public record ChatModelProperties(@Valid @NestedConfigurationProperty ApiProperties api) {

    public record ApiProperties(@DefaultValue("PT3M") Duration defaultTimeout) {}
  }

  public record ToolsProperties(
      @Valid @NestedConfigurationProperty ProcessDefinitionProperties processDefinition) {

    public record ProcessDefinitionProperties(
        @Valid @NestedConfigurationProperty RetriesProperties retries,
        @Valid @NestedConfigurationProperty CacheProperties cache) {

      public record RetriesProperties(
          @DefaultValue("4") @PositiveOrZero Integer maxRetries,
          @DefaultValue("PT0.5S") Duration initialRetryDelay) {}

      public record CacheProperties(
          @DefaultValue("true") boolean enabled,
          @DefaultValue("100") @PositiveOrZero Long maximumSize,
          @DefaultValue("PT10M") Duration expireAfterWrite) {}
    }
  }
}
