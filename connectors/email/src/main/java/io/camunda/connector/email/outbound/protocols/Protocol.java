/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols;

import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.outbound.protocols.actions.Action;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "Email Protocol",
    group = "protocol",
    name = "protocol",
    defaultValue = "smtp")
@TemplateSubType(id = "protocol", label = "Email Protocol")
public sealed interface Protocol permits Imap, Pop3, Smtp {
  Action getProtocolAction();

  Configuration getConfiguration();
}
