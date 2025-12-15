/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.config;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;

public record EmailPollingConfig(
    @TemplateProperty(
            label = "User ID/User Principal Name",
            feel = Property.FeelMode.optional,
            description = "The ID or Principal Name of the mailboxes owner.")
        @NotBlank
        @FEEL
        String userId,
    @NestedProperties() @Valid Folder folder,
    @TemplateProperty(
            id = "pollingInterval",
            label = "Polling interval",
            group = "pollingConfig",
            defaultValue = "PT30S",
            tooltip =
                "The interval between email polling requests, defined as ISO 8601 duration format. <a href='https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/#time-duration' target='_blank'>How to configure a time duration</a>",
            feel = Property.FeelMode.optional)
        @FEEL
        Duration pollingInterval,
    @NestedProperties() @Valid FilterCriteria filterCriteria) {
  public String getFilter() {

    return filterCriteria.getFilterString();
  }
}
