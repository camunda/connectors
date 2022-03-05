package io.camunda.connector.sendgrid;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sendgrid.Method;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendGridFunction implements HttpFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendGridFunction.class);
  private static final Gson GSON = new GsonBuilder().setVersion(0.1).create();

  @Override
  public void service(final HttpRequest httpRequest, final HttpResponse httpResponse)
      throws Exception {
    final var request = GSON.fromJson(httpRequest.getReader(), Request.class);
    LOGGER.info("Received request from cluster {}", request.clusterId);

    final var secretStore = new SecretStore(GSON, request.clusterId);
    request.replaceSecrets(secretStore);

    final var mail = createEmail(request, secretStore);
    final Response response = sendEmail(request.apiKey, mail);
    LOGGER.info("Received response from SendGrid with code {}", response.getStatusCode());

    httpResponse.setStatusCode(response.getStatusCode());
    Optional.ofNullable(response.getHeaders().get("Content-Type"))
        .ifPresent(httpResponse::setContentType);
    httpResponse.getWriter().write(response.getBody());
  }

  private Mail createEmail(final Request request, final SecretStore secretStore) {
    final var mail = new Mail();

    mail.setFrom(new Email(request.fromEmail, request.fromName));
    addContentIfPresent(mail, request);
    addTemplateIfPresent(mail, request);

    return mail;
  }

  private void addTemplateIfPresent(final Mail mail, final Request request) {
    if (request.hasTemplate()) {
      mail.setTemplateId(request.template.id);

      final var personalization = new Personalization();
      personalization.addTo(new Email(request.toEmail, request.toName));
      request.template.data.forEach(personalization::addDynamicTemplateData);
      mail.addPersonalization(personalization);
    }
  }

  private void addContentIfPresent(final Mail mail, final Request request) {
    if (request.hasContent()) {
      final Content content = request.content;
      mail.setSubject(content.subject);
      mail.addContent(new com.sendgrid.helpers.mail.objects.Content(content.type, content.value));
      final Personalization personalization = new Personalization();
      personalization.addTo(new Email(request.toEmail, request.toName));
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
