package io.camunda.connector.sendgrid;

import com.sendgrid.Method;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Personalization;

import io.camunda.connector.sdk.common.ConnectorFunction;
import io.camunda.connector.sdk.common.ConnectorContext;
import io.camunda.connector.sdk.common.ConnectorResult;
import io.camunda.connector.sdk.common.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SendGridFunction implements ConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendGridFunction.class);

  @Override
  public Object execute(ConnectorContext context) {

    final var request = context.getVariablesAsType(SendGridRequest.class);
    final var validator = new Validator();
    request.validate(validator);
    validator.validate();

    final var secretStore = context.getSecretStore();
    request.replaceSecrets(secretStore);

    final var mail = createEmail(request);

    try {
      final var result = sendEmail(request.getApiKey(), mail);
      final int statusCode = result.getStatusCode();
      LOGGER.info("Received response from SendGrid with code {}", statusCode);

      if (statusCode != 202) {
        final SendGridErrors errors = getGson().fromJson(result.getBody(), SendGridErrors.class);
        LOGGER.info("User request failed to execute with status {} and error '{}'", statusCode, errors);
        throw ConnectorResult.failed(errors.toString());
      }
    } catch (IOException exception) {
      throw ConnectorResult.failed(exception);
    }

    return ConnectorResult.empty();
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
      mail.addContent(new com.sendgrid.helpers.mail.objects.Content(content.getType(), content.getValue()));
      final Personalization personalization = new Personalization();
      personalization.addTo(request.getTo());
      mail.addPersonalization(personalization);
    }
  }

  private Response sendEmail(final String apiKey, final Mail mail) throws IOException {
    final SendGrid sg = new SendGrid(apiKey);
    final com.sendgrid.Request request = new com.sendgrid.Request();
    request.setMethod(Method.POST);
    request.setEndpoint("mail/send");
    request.setBody(mail.build());
    return sg.api(request);
  }

}
