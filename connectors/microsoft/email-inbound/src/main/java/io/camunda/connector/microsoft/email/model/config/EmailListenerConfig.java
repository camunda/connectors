/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.config;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.time.Duration;
import java.util.List;

public record EmailListenerConfig(
    String userId,
    List<String> select,
    @TemplateProperty(
            id = "pollingInterval",
            label = "Polling interval",
            group = "pollingConfig",
            defaultValue = "PT30S",
            tooltip =
                "The interval between email polling requests, defined as ISO 8601 duration format. <a href='https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/#time-duration' target='_blank'>How to configure a time duration</a>",
            binding = @TemplateProperty.PropertyBinding(name = "data.pollingInterval"),
            feel = Property.FeelMode.optional)
        @FEEL
        Duration pollingInterval) {
  public String[] selectAsArray() {
    return this.select.toArray(new String[0]);
  }
}
