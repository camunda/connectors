/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.model;

import io.camunda.connector.api.annotation.FEEL;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request model to map process/element instance variables. When mapping this model, the @FEEL
 * annotation on the clientResponse will resolve the actual response from the input mapping applied
 * to the element instance.
 */
public record A2aPollingElementInstanceRequest(
    @Valid @NotNull A2aTaskPollingElementInstanceRequestData data) {
  public record A2aTaskPollingElementInstanceRequestData(@NotBlank @FEEL String clientResponse) {}
}
