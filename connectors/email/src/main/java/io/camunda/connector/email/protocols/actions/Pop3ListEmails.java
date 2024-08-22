/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols.actions;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "listEmailsPop3", label = "List Emails using POP3")
public final class Pop3ListEmails implements Pop3Action {
  @TemplateProperty(
      label = "test",
      group = "listEmailsPop3",
      id = "data.test2",
      description = "",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.test2"))
  @Valid
  @NotNull
  String test;
}
