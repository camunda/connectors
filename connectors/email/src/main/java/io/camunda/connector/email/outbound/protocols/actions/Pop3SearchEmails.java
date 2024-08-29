/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateSubType(id = "searchEmailsPop3", label = "Search an email using POP3")
public final class Pop3SearchEmails implements Pop3Action {
  @TemplateProperty(
      label = "Search criteria",
      group = "searchEmailsPop3",
      id = "searchStringEmailPop3",
      description = "",
      feel = Property.FeelMode.required,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.criteria"))
  private Object criteria;
}
