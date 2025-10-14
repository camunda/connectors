/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai.a2a.client")
public record A2aConnectorConfigurationProperties(
    @NotNull @DefaultValue("true") Boolean enabled,
    @Valid @NotNull @DefaultValue TransportConfiguration transport) {

  public record TransportConfiguration(@Valid @NotNull @DefaultValue GrpcConfiguration grpc) {

    public record GrpcConfiguration(@DefaultValue("true") boolean useTls) {}
  }
}
