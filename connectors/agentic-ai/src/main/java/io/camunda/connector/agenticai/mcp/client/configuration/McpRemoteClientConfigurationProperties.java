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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai.mcp.remote-client")
public record McpRemoteClientConfigurationProperties(
    @NotNull @DefaultValue("true") Boolean enabled,
    @NotNull @Valid @DefaultValue ClientConfiguration client) {

  public record ClientConfiguration(@NotNull @Valid @DefaultValue ClientCacheConfiguration cache) {

    public record ClientCacheConfiguration(
        @DefaultValue("true") boolean enabled,
        @NotNull @PositiveOrZero @DefaultValue("15") Long maximumSize,
        @NotNull @DefaultValue("PT10M") Duration expireAfter) {}
  }
}
