/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model;

import io.camunda.connector.agenticai.a2a.common.model.A2aConnectionConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record A2aRequest(@Valid @NotNull A2aRequest.A2aRequestData data) {
  public record A2aRequestData(
      @Valid @NotNull A2aConnectionConfiguration connection,
      @Valid @NotNull A2aConnectorModeConfiguration connectorMode) {}
}
