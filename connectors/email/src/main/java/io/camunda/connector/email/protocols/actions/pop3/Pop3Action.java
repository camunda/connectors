/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols.actions.pop3;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "POP3 action",
    group = "pop3Action",
    name = "pop3Action",
    defaultValue = "sendEmail")
@TemplateSubType(id = "pop3Action", label = "POP3 Action")
public sealed interface Pop3Action permits Pop3DeleteEmail, Pop3ListEmails, Pop3ReadEmail {}
