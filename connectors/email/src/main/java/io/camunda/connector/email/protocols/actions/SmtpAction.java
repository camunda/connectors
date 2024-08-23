/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols.actions;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "SMTP action",
    group = "smtpAction",
    name = "data.smtpActionDiscriminator",
    defaultValue = "sendEmailSmtp")
@TemplateSubType(id = "data.smtpActionDiscriminator", label = "SMTP Action")
public sealed interface SmtpAction extends Action permits SmtpSendEmail {}
