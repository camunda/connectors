/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.email.core.jakarta.JakartaActionExecutor;
import io.camunda.connector.email.core.jakarta.JakartaSessionFactory;
import io.camunda.connector.email.outbound.model.EmailRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "Email",
    inputVariables = {"authentication", "protocol", "data"},
    type = "io.camunda:email:1")
@ElementTemplate(
    id = "io.camunda.connectors.email.v1",
    name = "Email Connector",
    description = "Execute email requests",
    inputDataClass = EmailRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "protocol", label = "Protocol"),
      @ElementTemplate.PropertyGroup(id = "smtpAction", label = "SMTP Action"),
      @ElementTemplate.PropertyGroup(id = "pop3Action", label = "POP3 Action"),
      @ElementTemplate.PropertyGroup(id = "imapAction", label = "IMAP Action"),
      @ElementTemplate.PropertyGroup(id = "sendEmailSmtp", label = "Send Email using SMTP"),
      @ElementTemplate.PropertyGroup(id = "listEmailsPop3", label = "List Emails using POP3"),
      @ElementTemplate.PropertyGroup(id = "deleteEmailPop3", label = "Delete Email using POP3"),
      @ElementTemplate.PropertyGroup(id = "readEmailPop3", label = "Read Email using POP3"),
      @ElementTemplate.PropertyGroup(id = "listEmailImap", label = "List Email using IMAP"),
      @ElementTemplate.PropertyGroup(id = "readEmailImap", label = "Read Email using IMAP"),
      @ElementTemplate.PropertyGroup(id = "deleteEmailImap", label = "Read Email using IMAP"),
      @ElementTemplate.PropertyGroup(id = "moveEmailsImap", label = "Move Emails using IMAP")
    },
    documentationRef = "https://docs.camunda.io/docs/",
    icon = "icon.svg")
public class EmailConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    EmailRequest emailRequest = context.bindVariables(EmailRequest.class);
    return JakartaActionExecutor.create(new JakartaSessionFactory()).execute(emailRequest);
  }
}
