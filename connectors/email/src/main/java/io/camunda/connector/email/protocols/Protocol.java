/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "Protocol",
    group = "protocol",
    name = "protocol",
    defaultValue = "SMTP")
@TemplateSubType(id = "protocol", label = "Protocol")
public sealed interface Protocol permits Imap, Pop3, Smtp {
  Object execute(Authentication authentication);
}
