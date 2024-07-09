/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@TemplateSubType(id = "converse", label = "Converse")
public final class ConversePayload implements RequestData {
  @TemplateProperty(
      label = "User Message List",
      id = "payload.userList",
      group = "converse",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "payload.userList"))
  @NotEmpty
  private List<String> userMessages;

  @TemplateProperty(
      label = "Assistant Message List",
      id = "payload.assistantList",
      group = "converse",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "payload.assistantList"))
  private List<String> assistantMessages;

  @Override
  public BedrockResponse execute() {
    return null;
  }
}
