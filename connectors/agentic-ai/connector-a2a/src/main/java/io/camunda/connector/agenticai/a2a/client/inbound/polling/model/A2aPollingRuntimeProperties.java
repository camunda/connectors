/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.polling.model;

import io.camunda.connector.agenticai.a2a.client.common.model.A2aConnectionConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record A2aPollingRuntimeProperties(@Valid @NotNull A2aPollingRuntimePropertiesData data) {
  public record A2aPollingRuntimePropertiesData(
      @Valid @NotNull A2aConnectionConfiguration connection,
      @NotBlank
          @FEEL
          @TemplateProperty(
              id = "clientResponse",
              group = "clientResponse",
              label = "A2A Client Response",
              description =
                  "The response returned by the A2A client connector. Should contain a task, a message, or an agent card.",
              binding = @TemplateProperty.PropertyBinding(name = "clientResponse"),
              feel = FeelMode.required)
          String clientResponse,
      @PositiveOrZero
          @FEEL
          @TemplateProperty(
              id = "historyLength",
              group = "options",
              label = "History length",
              description =
                  "The number of messages to return as part of the history when polling a task.",
              binding = @TemplateProperty.PropertyBinding(name = "historyLength"),
              feel = FeelMode.optional,
              defaultValueType = TemplateProperty.DefaultValueType.Number,
              defaultValue = "3")
          Integer historyLength) {
    public A2aPollingRuntimePropertiesData {
      if (historyLength == null) {
        historyLength = 3;
      }
    }
  }
}
