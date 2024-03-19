/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Method;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Personalization;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.sendgrid.model.SendGridRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "SendGrid",
    inputVariables = {"apiKey", "from", "to", "template", "content"},
    type = "io.camunda:sendgrid:1")
@ElementTemplate(
    id = "io.camunda.connectors.SendGrid.v2",
    name = "SendGrid Outbound Connector",
    description = "Send an email via SendGrid",
    inputDataClass = SendGridRequest.class,
    version = 3,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "sender", label = "sender"),
      @ElementTemplate.PropertyGroup(id = "receiver", label = "Receiver"),
      @ElementTemplate.PropertyGroup(id = "content", label = "Compose email")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/sendgrid/",
    icon = "icon.svg")
public class SendGridFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendGridFunction.class);

  protected static final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final SendGridClientSupplier sendGridSupplier;

  public SendGridFunction() {
    this(new SendGridClientSupplier());
  }

  public SendGridFunction(SendGridClientSupplier sendGridSupplier) {
    this.sendGridSupplier = sendGridSupplier;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    final var request = context.bindVariables(SendGridRequest.class);
    SendGrid sendGrid = sendGridSupplier.sendGrid(request.getApiKey());
    final var mail = createEmail(request);
    final var result = sendEmail(mail, sendGrid);
    final int statusCode = result.getStatusCode();
    LOGGER.info("Received response from SendGrid with code {}", statusCode);
    if (statusCode != 202) {
      final SendGridErrors errors = objectMapper.readValue(result.getBody(), SendGridErrors.class);
      final var exceptionMessage =
          String.format(
              "User request failed to execute with status %s and error '%s'",
              statusCode, errors.toString());
      LOGGER.info(exceptionMessage);
      throw new IllegalArgumentException(exceptionMessage);
    }
    return result;
  }

  private Mail createEmail(final SendGridRequest request) {
    final var mail = new Mail();

    mail.setFrom(request.getInnerSenGridEmailFrom());
    addContentIfPresent(mail, request);
    addTemplateIfPresent(mail, request);

    return mail;
  }

  private void addTemplateIfPresent(final Mail mail, final SendGridRequest request) {
    if (request.hasTemplate()) {
      mail.setTemplateId(request.getTemplate().id());
      final var personalization = new Personalization();
      personalization.addTo(request.getInnerSenGridEmailTo());
      request.getTemplate().data().forEach(personalization::addDynamicTemplateData);
      mail.addPersonalization(personalization);
    }
  }

  private void addContentIfPresent(final Mail mail, final SendGridRequest request) {
    if (request.hasContent()) {
      final SendGridRequest.Content content = request.getContent();
      mail.setSubject(content.subject());
      mail.addContent(
          new com.sendgrid.helpers.mail.objects.Content(content.type(), content.value()));
      final Personalization personalization = new Personalization();
      personalization.addTo(request.getInnerSenGridEmailTo());
      mail.addPersonalization(personalization);
    }
  }

  private Response sendEmail(final Mail mail, final SendGrid sendGrid) throws IOException {
    final com.sendgrid.Request request = new com.sendgrid.Request();
    request.setMethod(Method.POST);
    request.setEndpoint("mail/send");
    request.setBody(mail.build());
    return sendGrid.api(request);
  }
}
