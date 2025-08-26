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
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai")
public record AgenticAiConnectorsConfigurationProperties(
    @Valid @NotNull @DefaultValue ToolsSchemaConfiguration tools) {
  public record ToolsSchemaConfiguration(@Valid @NotNull @DefaultValue CacheConfiguration cache) {
    public record CacheConfiguration(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") @PositiveOrZero Long maximumSize,
        @DefaultValue("PT10M") Duration expireAfterWrite) {}
  }
}
