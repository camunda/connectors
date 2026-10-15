/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema.configuration;

import io.camunda.connector.agenticai.common.configuration.RetriesProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai.tools")
public record ToolsSchemaProperties(
    @Valid @DefaultValue @NestedConfigurationProperty
        ProcessDefinitionProperties processDefinition) {

  public record ProcessDefinitionProperties(
      @Valid @DefaultValue @NestedConfigurationProperty RetriesProperties retries,
      @Valid @DefaultValue CacheProperties cache) {

    public record CacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") @PositiveOrZero Long maximumSize,
        @DefaultValue("PT10M") Duration expireAfterWrite) {}
  }
}
