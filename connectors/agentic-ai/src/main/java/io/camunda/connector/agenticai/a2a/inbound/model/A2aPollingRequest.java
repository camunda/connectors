/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest.A2aRequestData.ConnectionConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;

public record A2aPollingRequest(@Valid @NotNull A2aTaskPollingRequestData data) {
  public record A2aTaskPollingRequestData(
      @Valid @NotNull ConnectionConfiguration connection,
      @NotBlank
          @TemplateProperty(
              id = "clientResponse",
              group = "clientResponse",
              label = "A2A Client Response",
              binding = @TemplateProperty.PropertyBinding(name = "clientResponse"),
              feel = Property.FeelMode.required)
          String clientResponse,
      @PositiveOrZero
          @TemplateProperty(
              id = "historyLength",
              group = "options",
              label = "History length",
              binding = @TemplateProperty.PropertyBinding(name = "historyLength"),
              feel = Property.FeelMode.optional,
              defaultValueType = TemplateProperty.DefaultValueType.Number,
              defaultValue = "3")
          Integer historyLength,
      @JsonSetter(nulls = Nulls.SKIP)
          @TemplateProperty(
              id = "processPollingInterval",
              group = "polling",
              label = "Process polling interval",
              defaultValue = "PT5S",
              type = TemplateProperty.PropertyType.Hidden,
              binding = @TemplateProperty.PropertyBinding(name = "processPollingInterval"))
          @FEEL
          Duration processPollingInterval,
      @JsonSetter(nulls = Nulls.SKIP)
          @TemplateProperty(
              id = "taskPollingInterval",
              group = "polling",
              label = "Task polling interval",
              defaultValue = "PT10S",
              binding = @TemplateProperty.PropertyBinding(name = "taskPollingInterval"),
              feel = Property.FeelMode.optional)
          @FEEL
          Duration taskPollingInterval) {

    public A2aTaskPollingRequestData {
      if (historyLength == null) {
        historyLength = 3;
      }

      if (processPollingInterval == null) {
        processPollingInterval = Duration.ofSeconds(5);
      }

      if (taskPollingInterval == null) {
        taskPollingInterval = Duration.ofSeconds(10);
      }
    }
  }
}
