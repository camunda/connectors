/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public record EventHandlingConfiguration(
    @TemplateProperty(
            label = "Event handling behavior",
            description = "Behavior in combination with an event sub-process.",
            group = "events",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "WAIT_FOR_TOOL_CALL_RESULTS",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "WAIT_FOR_TOOL_CALL_RESULTS",
                  label = "Wait for tool call results"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "INTERRUPT_TOOL_CALLS",
                  label = "Interrupt tool calls")
            })
        @NotNull
        EventHandlingBehavior behavior) {

  public enum EventHandlingBehavior {
    WAIT_FOR_TOOL_CALL_RESULTS,
    INTERRUPT_TOOL_CALLS
  }
}
