/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.utils;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.models.Email;
import io.camunda.connector.email.client.jakarta.models.EmailAttachment;
import io.camunda.connector.email.client.jakarta.models.EmailBody;
import io.camunda.connector.email.client.jakarta.models.Header;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.config.Pop3Config;
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.email.outbound.protocols.actions.SortFieldImap;
import io.camunda.connector.email.outbound.protocols.actions.SortFieldPop3;
import io.camunda.connector.email.outbound.protocols.actions.SortOrder;
import jakarta.mail.*;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMultipart;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JakartaUtils {

  public static final String HTML_CHARSET = "text/html; charset=utf-8";
  private static final Logger LOGGER = LoggerFactory.getLogger(JakartaUtils.class);
  private static final String REGEX_PATH_SPLITTER = "[./]";

  public Session createSession(Configuration configuration) {
    return Session.getInstance(
        switch (configuration) {
          case ImapConfig imap -> createProperties(imap);
          case Pop3Config pop3 -> createProperties(pop3);
          case SmtpConfig smtp -> createProperties(smtp);
        });
  }

  public void connectStore(Store store, Authentication authentication) throws MessagingException {
    switch (authentication) {
      case SimpleAuthentication simpleAuthentication ->
          store.connect(simpleAuthentication.username(), simpleAuthentication.password());
    }
  }

  public void connectTransport(Transport transport, Authentication authentication)
      throws MessagingException {
    switch (authentication) {
      case SimpleAuthentication simpleAuthentication ->
          transport.connect(simpleAuthentication.username(), simpleAuthentication.password());
    }
  }

  public void markAsDeleted(Message message) {
    try {
      message.setFlag(Flags.Flag.DELETED, true);
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  public void markAsSeen(Message message) {
    try {
      message.setFlag(Flags.Flag.SEEN, true);
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private Properties createProperties(SmtpConfig smtp) {
    Properties properties = new Properties();
    properties.put("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.host", smtp.smtpHost().trim());
    properties.put("mail.smtp.port", smtp.smtpPort().toString());
    properties.put("mail.smtp.auth", true);
    switch (smtp.smtpCryptographicProtocol()) {
      case NONE -> {}
      case TLS -> properties.put("mail.smtp.starttls.enable", true);
      case SSL -> properties.put("mail.smtp.ssl.enable", true);
    }
    return properties;
  }

  private Properties createProperties(Pop3Config pop3) {
    Properties properties = new Properties();

    switch (pop3.pop3CryptographicProtocol()) {
      case NONE -> {
        properties.put("mail.store.protocol", "pop3");
        properties.put("mail.pop3.host", pop3.pop3Host().trim());
        properties.put("mail.pop3.port", pop3.pop3Port().toString());
        properties.put("mail.pop3.auth", true);
      }
      case TLS -> {
        properties.put("mail.store.protocol", "pop3s");
        properties.put("mail.pop3s.host", pop3.pop3Host().trim());
        properties.put("mail.pop3s.port", pop3.pop3Port().toString());
        properties.put("mail.pop3s.auth", true);
        properties.put("mail.pop3s.starttls.enable", true);
      }
      case SSL -> {
        properties.put("mail.store.protocol", "pop3s");
        properties.put("mail.pop3s.host", pop3.pop3Host().trim());
        properties.put("mail.pop3s.port", pop3.pop3Port().toString());
        properties.put("mail.pop3s.auth", true);
        properties.put("mail.pop3s.ssl.enable", true);
      }
    }
    return properties;
  }

  private Properties createProperties(ImapConfig imap) {
    Properties properties = new Properties();

    switch (imap.imapCryptographicProtocol()) {
      case NONE -> {
        properties.put("mail.store.protocol", "imap");
        properties.put("mail.imap.host", imap.imapHost().trim());
        properties.put("mail.imap.port", imap.imapPort().toString());
        properties.put("mail.imap.auth", true);
        properties.put("mail.imap.timeout", "10000");
      }
      case TLS -> {
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", imap.imapHost().trim());
        properties.put("mail.imaps.port", imap.imapPort().toString());
        properties.put("mail.imaps.auth", true);
        properties.put("mail.imaps.starttls.enable", true);
        properties.put("mail.imaps.timeout", "10000");
      }
      case SSL -> {
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", imap.imapHost().trim());
        properties.put("mail.imaps.port", imap.imapPort().toString());
        properties.put("mail.imaps.auth", true);
        properties.put("mail.imaps.ssl.enable", true);
        properties.put("mail.imaps.timeout", "10000");
      }
    }
    return properties;
  }

  public Comparator<Email> retrieveEmailComparator(
      @NotNull SortFieldPop3 sortFieldPop3, @NotNull SortOrder sortOrder) {
    return (email1, email2) ->
        switch (sortFieldPop3) {
          case SENT_DATE -> sortOrder.order(email1.sentAt().compareTo(email2.sentAt()));
          case SIZE -> sortOrder.order(email1.size().compareTo(email2.size()));
        };
  }

  public Comparator<Email> retrieveEmailComparator(
      @NotNull SortFieldImap sortFieldImap, @NotNull SortOrder sortOrder) {
    return (email1, email2) ->
        switch (sortFieldImap) {
          case RECEIVED_DATE -> sortOrder.order(email1.receivedAt().compareTo(email2.receivedAt()));
          case SENT_DATE -> sortOrder.order(email1.sentAt().compareTo(email2.sentAt()));
          case SIZE -> sortOrder.order(email1.size().compareTo(email2.size()));
        };
  }

  public Folder findImapFolder(Store store, String folderPath) throws MessagingException {
    if (folderPath == null || folderPath.isEmpty() || "INBOX".equalsIgnoreCase(folderPath)) {
      return store.getFolder("INBOX");
    }
    char separator = store.getDefaultFolder().getSeparator();
    String formattedPath =
        Optional.of(folderPath)
            .map(string -> string.split(REGEX_PATH_SPLITTER))
            .map(strings -> String.join(String.valueOf(separator), strings))
            .orElseThrow(() -> new MessagingException("No folder has been set"));
    Folder folder = store.getFolder(formattedPath);
    if (!folder.exists())
      throw new MessagingException("Folder " + formattedPath + " does not exist");
    return folder;
  }

  public Email createBodylessEmail(Message message) {
    try {
      String from =
          Arrays.stream(Optional.ofNullable(message.getFrom()).orElse(new Address[0]))
              .map(Address::toString)
              .map(address -> address.replaceAll(".*<|>.*", ""))
              .toList()
              .getFirst();
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
      List<io.camunda.connector.email.client.jakarta.models.Header> headers =
          Collections.list(message.getAllHeaders()).stream()
              .map(header -> new Header(header.getName(), header.getValue()))
              .toList();
      String messageId = getMessageId(message);
      return new Email(
          null,
          messageId,
          message.getSubject(),
          headers,
          from,
          to,
          cc,
          sentAt,
          receivedAt,
          message.getSize());

    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  public Email createEmail(Message message) {
    try {
      Object bodyObject = message.getContent();
      Email email = this.createBodylessEmail(message);
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
          email.messageId(),
          email.subject(),
          email.headers(),
          email.from(),
          email.to(),
          email.cc(),
          email.sentAt(),
          email.receivedAt(),
          email.size());
    } catch (IOException | MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private EmailBody processMultipart(
      MimeMultipart multipart, EmailBody.EmailBodyBuilder emailBodyBuilder) {
    try {
      for (int i = 0; i < multipart.getCount(); i++) {
        processBodyPart(multipart, emailBodyBuilder, i);
      }
      return emailBodyBuilder.build();
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void processBodyPart(
      MimeMultipart multipart, EmailBody.EmailBodyBuilder emailBodyBuilder, int i)
      throws MessagingException, IOException {
    BodyPart bodyPart = multipart.getBodyPart(i);
    switch (bodyPart.getContent()) {
      case InputStream attachment when Part.ATTACHMENT.equalsIgnoreCase(
              bodyPart.getDisposition()) ->
          emailBodyBuilder.addAttachment(
              new EmailAttachment(
                  attachment,
                  bodyPart.getFileName(),
                  new ContentType(bodyPart.getContentType()).getBaseType()));
      case String textAttachment when Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) ->
          emailBodyBuilder.addAttachment(
              new EmailAttachment(
                  new ByteArrayInputStream(textAttachment.getBytes()),
                  bodyPart.getFileName(),
                  new ContentType(bodyPart.getContentType()).getBaseType()));
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

  private String getMessageId(Message message) {
    try {
      String[] messageIds = message.getHeader("Message-ID");
      return (messageIds != null && messageIds.length > 0) ? messageIds[0] : null;
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  public void moveMessage(Store store, Message message, String targetFolder) {
    try {
      Folder imapFolder = message.getFolder();
      char separator = imapFolder.getSeparator();
      String targetFolderFormatted =
          Optional.ofNullable(targetFolder)
              .map(string -> string.split(REGEX_PATH_SPLITTER))
              .map(strings -> String.join(String.valueOf(separator), strings))
              .orElseThrow(() -> new RuntimeException("No folder has been set"));
      Folder targetImapFolder = store.getFolder(targetFolderFormatted);
      if (!targetImapFolder.exists()) targetImapFolder.create(Folder.HOLDS_MESSAGES);
      targetImapFolder.open(Folder.READ_WRITE);
      imapFolder.copyMessages(new Message[] {message}, targetImapFolder);
      this.markAsDeleted(message);
      targetImapFolder.close();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }
}
