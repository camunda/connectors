/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.core;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Email {

  private static final Logger LOGGER = LoggerFactory.getLogger(Email.class);
  private final EmailBody body;
  private final String subject;
  private final List<String> from;
  private final List<String> to;
  private final List<String> cc;
  private final OffsetDateTime sentAt;
  private final OffsetDateTime receivedAt;
  private final Integer size;

  private Email(
      EmailBody body,
      String subject,
      List<String> from,
      List<String> to,
      List<String> cc,
      OffsetDateTime sentAt,
      OffsetDateTime receivedAt,
      Integer size) {
    this.body = body;
    this.subject = subject;
    this.from = from;
    this.to = to;
    this.cc = cc;
    this.sentAt = sentAt;
    this.receivedAt = receivedAt;
    this.size = size;
  }

  public static Email createBodylessEmail(Message message) {
    try {
      List<String> from =
          Arrays.stream(Optional.ofNullable(message.getFrom()).orElse(new Address[0]))
              .map(Address::toString)
              .map(address -> address.replaceAll(".*<|>.*", ""))
              .toList();
      List<String> to =
          Arrays.stream(
                  Optional.ofNullable(message.getRecipients(Message.RecipientType.TO))
                      .orElse(new Address[0]))
              .map(Address::toString)
              .map(address -> address.replaceAll(".*<|>.*", ""))
              .toList();
      List<String> cc =
          Arrays.stream(
                  Optional.ofNullable(message.getRecipients(Message.RecipientType.CC))
                      .orElse(new Address[0]))
              .map(Address::toString)
              .map(address -> address.replaceAll(".*<|>.*", ""))
              .toList();
      OffsetDateTime sentAt =
          Optional.ofNullable(message.getSentDate())
              .map(Date::toInstant)
              .map(instant -> instant.atOffset(ZoneOffset.UTC))
              .orElse(null);
      OffsetDateTime receivedAt =
          Optional.ofNullable(message.getReceivedDate())
              .map(Date::toInstant)
              .map(instant -> instant.atOffset(ZoneOffset.UTC))
              .orElse(null);
      return new Email(
          null, message.getSubject(), from, to, cc, sentAt, receivedAt, message.getSize());

    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  public static Email createEmail(Message message) {
    try {
      Object bodyObject = message.getContent();
      Email email = Email.createBodylessEmail(message);
      EmailBody emailBody =
          switch (bodyObject) {
            case MimeMultipart multipart -> processMultipart(multipart, EmailBody.createBuilder());
            case String bodyAsPlainText when message.isMimeType("text/plain") ->
                EmailBody.createBuilder().withBodyAsPlainText(bodyAsPlainText).build();
            case String bodyAsHtml when message.isMimeType("text/html") ->
                EmailBody.createBuilder().withBodyAsHtml(bodyAsHtml).build();
            default -> throw new IllegalStateException("Unexpected part: " + bodyObject);
          };
      return new Email(
          emailBody,
          email.getSubject(),
          email.getFrom(),
          email.getTo(),
          email.getCc(),
          email.getSentAt(),
          email.getReceivedAt(),
          email.getSize());
    } catch (IOException | MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private static EmailBody processMultipart(
      MimeMultipart multipart, EmailBody.EmailBodyBuilder emailBodyBuilder) {
    try {
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart bodyPart = multipart.getBodyPart(i);
        switch (bodyPart.getContent()) {
          case InputStream attachment when bodyPart
                  .getDisposition()
                  .equalsIgnoreCase(Part.ATTACHMENT) ->
              emailBodyBuilder.withAttachment(attachment);
          case String plainText when bodyPart.isMimeType("text/plain") ->
              emailBodyBuilder.withBodyAsPlainText(plainText);
          case String html when bodyPart.isMimeType("text/html") ->
              emailBodyBuilder.withBodyAsHtml(html);
          case MimeMultipart nestedMultipart -> processMultipart(nestedMultipart, emailBodyBuilder);
          default ->
              LOGGER.warn(
                  "This part is not yet managed. Mime : {}, disposition: {}",
                  bodyPart.getContentType(),
                  bodyPart.getDisposition());
        }
      }
      return emailBodyBuilder.build();
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  public OffsetDateTime getReceivedAt() {
    return receivedAt;
  }

  public OffsetDateTime getSentAt() {
    return sentAt;
  }

  public List<String> getCc() {
    return cc;
  }

  public List<String> getTo() {
    return to;
  }

  public List<String> getFrom() {
    return from;
  }

  public String getSubject() {
    return subject;
  }

  public EmailBody getBody() {
    return body;
  }

  public Integer getSize() {
    return size;
  }

  public static class EmailBody {
    private final String bodyAsPlainText;
    private final String bodyAsHtml;
    private final List<InputStream> attachments;

    private EmailBody(String bodyAsPlainText, String bodyAsHtml, List<InputStream> attachments) {
      this.bodyAsPlainText = bodyAsPlainText;
      this.bodyAsHtml = bodyAsHtml;
      this.attachments = attachments;
    }

    public static EmailBodyBuilder createBuilder() {
      return new EmailBodyBuilder();
    }

    public String getBodyAsPlainText() {
      return bodyAsPlainText;
    }

    public String getBodyAsHtml() {
      return bodyAsHtml;
    }

    public List<InputStream> getAttachments() {
      return attachments;
    }

    public static class EmailBodyBuilder {
      private final List<InputStream> attachments = new ArrayList<>();
      private String bodyAsPlainText;
      private String bodyAsHtml;

      public EmailBodyBuilder withBodyAsPlainText(String bodyAsPlainText) {
        this.bodyAsPlainText = bodyAsPlainText;
        return this;
      }

      public EmailBodyBuilder withBodyAsHtml(String bodyAsHtml) {
        this.bodyAsHtml = bodyAsHtml;
        return this;
      }

      public EmailBodyBuilder withAttachment(InputStream attachment) {
        if (attachment != null) {
          this.attachments.add(attachment);
        }
        return this;
      }

      public EmailBodyBuilder withAttachments(List<InputStream> attachments) {
        if (attachments != null) {
          this.attachments.addAll(attachments);
        }
        return this;
      }

      public EmailBody build() {
        return new EmailBody(bodyAsPlainText, bodyAsHtml, attachments);
      }
    }
  }
}
