/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.polling.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public record A2aPollingActivationProperties(
    @Valid @NotNull A2aPollingActivationPropertiesData data) {
  public record A2aPollingActivationPropertiesData(
      @JsonSetter(nulls = Nulls.SKIP)
          @TemplateProperty(
              id = "processPollingInterval",
              group = "polling",
              defaultValue = "PT5S",
              type = TemplateProperty.PropertyType.Hidden,
              binding = @TemplateProperty.PropertyBinding(name = "processPollingInterval"))
          @FEEL
          Duration processPollingInterval,
      @JsonSetter(nulls = Nulls.SKIP)
          @TemplateProperty(
              id = "taskPollingInterval",
              group = "polling",
              defaultValue = "PT10S",
              binding = @TemplateProperty.PropertyBinding(name = "taskPollingInterval"),
              description =
                  "The delay between A2A task polling requests, defined as ISO 8601 durations format. <a href='https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/#time-duration' target='_blank'>How to configure a time duration</a>",
              feel = FeelMode.optional)
          @FEEL
          Duration taskPollingInterval) {
    public A2aPollingActivationPropertiesData {
      if (processPollingInterval == null) {
        processPollingInterval = Duration.ofSeconds(5);
      }

      if (taskPollingInterval == null) {
        taskPollingInterval = Duration.ofSeconds(10);
      }
    }
  }
}
