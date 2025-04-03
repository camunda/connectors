/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.email.client.EmailActionExecutor;
import io.camunda.connector.email.client.jakarta.outbound.JakartaEmailActionExecutor;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.outbound.model.EmailRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "Email",
    inputVariables = {"authentication", "protocol", "data"},
    type = "io.camunda:email:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connectors.email.v1",
    name = "Email Connector",
    description = "Execute email requests",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {
              "send emails",
              "list emails",
              "search emails",
              "delete emails",
              "read emails",
              "move emails"
            }),
    inputDataClass = EmailRequest.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "protocol", label = "Protocol"),
      @ElementTemplate.PropertyGroup(id = "smtpAction", label = "SMTP Action"),
      @ElementTemplate.PropertyGroup(id = "pop3Action", label = "POP3 Action"),
      @ElementTemplate.PropertyGroup(id = "imapAction", label = "IMAP Action"),
      @ElementTemplate.PropertyGroup(id = "sendEmailSmtp", label = "Send Email"),
      @ElementTemplate.PropertyGroup(id = "listEmailsPop3", label = "List Emails"),
      @ElementTemplate.PropertyGroup(id = "searchEmailsPop3", label = "Search Emails"),
      @ElementTemplate.PropertyGroup(id = "deleteEmailPop3", label = "Delete Email"),
      @ElementTemplate.PropertyGroup(id = "readEmailPop3", label = "Read Email"),
      @ElementTemplate.PropertyGroup(id = "listEmailsImap", label = "List Email"),
      @ElementTemplate.PropertyGroup(id = "searchEmailsImap", label = "Search Emails"),
      @ElementTemplate.PropertyGroup(id = "readEmailImap", label = "Read Email"),
      @ElementTemplate.PropertyGroup(id = "deleteEmailImap", label = "Read Email"),
      @ElementTemplate.PropertyGroup(id = "moveEmailImap", label = "Move Emails")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email/",
    icon = "icon.svg")
public class EmailConnectorFunction implements OutboundConnectorFunction {

  private final EmailActionExecutor emailActionExecutor;

  public EmailConnectorFunction() {
    this(
        JakartaEmailActionExecutor.create(
            new JakartaUtils(), ConnectorsObjectMapperSupplier.getCopy()));
  }

  public EmailConnectorFunction(EmailActionExecutor emailActionExecutor) {
    this.emailActionExecutor = emailActionExecutor;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    return emailActionExecutor.execute(context);
  }
}
