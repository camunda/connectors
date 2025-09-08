/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record A2AClientAsToolRequest(@Valid @NotNull A2AClientRequestData data) {
  public record A2AClientRequestData(
      @Valid @NotNull A2AClientRequest.A2AClientRequestData.ConnectionConfiguration connection,
      @Valid @NotNull A2AClientAsToolOperationConfiguration operation) {}
}
