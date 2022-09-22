/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import com.google.gson.Gson;
import com.sendgrid.Method;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Personalization;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendGridFunction implements ConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendGridFunction.class);

  private final Gson gson;

  private final SendGridClientSupplier sendGridSupplier;

  public SendGridFunction() {
    this(GsonComponentSupplier.gsonInstance(), new SendGridClientSupplier());
  }

  public SendGridFunction(final Gson gson, SendGridClientSupplier sendGridSupplier) {
    this.gson = gson;
    this.sendGridSupplier = sendGridSupplier;
  }

  @Override
  public Object execute(ConnectorContext context) throws Exception {

    final var request = context.getVariablesAsType(SendGridRequest.class);
    context.validate(request);
    context.replaceSecrets(request);

    SendGrid sendGrid = sendGridSupplier.sendGrid(request.getApiKey());

    final var mail = createEmail(request);
    final var result = sendEmail(mail, sendGrid);

    final int statusCode = result.getStatusCode();
    LOGGER.info("Received response from SendGrid with code {}", statusCode);

    if (statusCode != 202) {
      final SendGridErrors errors = gson.fromJson(result.getBody(), SendGridErrors.class);
      final var exceptionMessage =
          String.format(
              "User request failed to execute with status %s and error '%s'",
              statusCode, errors.toString());
      LOGGER.info(exceptionMessage);
      throw new IllegalArgumentException(exceptionMessage);
    }

    return null;
  }

  private Mail createEmail(final SendGridRequest request) {
    final var mail = new Mail();

    mail.setFrom(request.getFrom());
    addContentIfPresent(mail, request);
    addTemplateIfPresent(mail, request);

    return mail;
  }

  private void addTemplateIfPresent(final Mail mail, final SendGridRequest request) {
    if (request.hasTemplate()) {
      mail.setTemplateId(request.getTemplate().getId());

      final var personalization = new Personalization();
      personalization.addTo(request.getTo());
      request.getTemplate().getData().forEach(personalization::addDynamicTemplateData);
      mail.addPersonalization(personalization);
    }
  }

  private void addContentIfPresent(final Mail mail, final SendGridRequest request) {
    if (request.hasContent()) {
      final SendGridContent content = request.getContent();
      mail.setSubject(content.getSubject());
      mail.addContent(
          new com.sendgrid.helpers.mail.objects.Content(content.getType(), content.getValue()));
      final Personalization personalization = new Personalization();
      personalization.addTo(request.getTo());
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
