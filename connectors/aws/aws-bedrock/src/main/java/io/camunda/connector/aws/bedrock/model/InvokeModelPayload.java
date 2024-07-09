/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import io.camunda.connector.aws.bedrock.core.InvokeModelModel;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "invokeModel", label = "Invoke Model")
public final class InvokeModelPayload implements RequestData {
  @TemplateProperty(
      label = "Message for bedrock",
      id = "payload.userMessage",
      group = "invokeModel",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "payload.userMessage"))
  @NotBlank
  String userMessage;

  @TemplateProperty(
      label = "Model to be used by bedrock",
      id = "payload.model",
      group = "invokeModel",
      feel = Property.FeelMode.optional,
      defaultValue = "Jamba-Instruct",
      type = TemplateProperty.PropertyType.Dropdown,
      choices = {
        @TemplateProperty.DropdownPropertyChoice(value = "Jamba-Instruct", label = "Jamba Instruct")
      },
      binding = @TemplateProperty.PropertyBinding(name = "payload.model"))
  @NotBlank
  InvokeModelModel model;

  public void setModel(@NotBlank InvokeModelModel model) {
    this.model = model;
  }

  public @NotBlank String getUserMessage() {
    return userMessage;
  }

  public void setUserMessage(@NotBlank String userMessage) {
    this.userMessage = userMessage;
  }

  @Override
  public BedrockResponse execute() {
    return null;
  }
}
