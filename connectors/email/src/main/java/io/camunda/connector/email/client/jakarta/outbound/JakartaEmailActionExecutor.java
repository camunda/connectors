/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.client.EmailActionExecutor;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.outbound.model.EmailRequest;
import io.camunda.connector.email.outbound.protocols.Protocol;
import io.camunda.connector.email.outbound.protocols.actions.*;
import io.camunda.connector.email.response.*;
import jakarta.mail.*;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.*;
import java.util.*;

public class JakartaEmailActionExecutor implements EmailActionExecutor {

  private final JakartaUtils jakartaUtils;
  private final ObjectMapper objectMapper;

  private JakartaEmailActionExecutor(JakartaUtils jakartaUtils, ObjectMapper objectMapper) {
    this.jakartaUtils = jakartaUtils;
    this.objectMapper = objectMapper;
  }

  public static JakartaEmailActionExecutor create(
      JakartaUtils sessionFactory, ObjectMapper objectMapper) {
    return new JakartaEmailActionExecutor(sessionFactory, objectMapper);
  }

  public Object execute(EmailRequest emailRequest) {
    Authentication authentication = emailRequest.authentication();
    Protocol protocol = emailRequest.data();
    Action action = protocol.getProtocolAction();
    Session session = jakartaUtils.createSession(protocol.getConfiguration());
    return switch (action) {
      case SmtpSendEmail smtpSendEmail -> smtpSendEmail(smtpSendEmail, authentication, session);
      case ImapMoveEmail imapMoveEmail -> imapMoveEmails(imapMoveEmail, authentication, session);
      case ImapListEmails imapListEmails -> imapListEmails(imapListEmails, authentication, session);
      case ImapDeleteEmail imapDeleteEmail ->
          imapDeleteEmail(imapDeleteEmail, authentication, session);
      case ImapSearchEmails imapSearchEmails ->
          imapSearchEmails(imapSearchEmails, authentication, session);
      case ImapReadEmail imapReadEmail -> imapReadEmail(imapReadEmail, authentication, session);
      case Pop3DeleteEmail pop3DeleteEmail ->
          pop3DeleteEmail(pop3DeleteEmail, authentication, session);
      case Pop3ListEmails pop3ListEmails -> pop3ListEmails(pop3ListEmails, authentication, session);
      case Pop3ReadEmail pop3ReadEmail -> pop3ReadEmail(pop3ReadEmail, authentication, session);
      case Pop3SearchEmails pop3SearchEmails ->
          pop3SearchEmails(pop3SearchEmails, authentication, session);
    };
  }

  private List<SearchEmailsResponse> imapSearchEmails(
      ImapSearchEmails imapSearchEmails, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.jakartaUtils.connectStore(store, authentication);
      Folder defaultFolder = store.getDefaultFolder();
      String targetFolder = imapSearchEmails.searchEmailFolder();
      try (Folder imapFolder = this.jakartaUtils.findImapFolder(defaultFolder, targetFolder)) {
        return searchEmails(imapFolder, imapSearchEmails.criteria());
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private ReadEmailResponse imapReadEmail(
      ImapReadEmail imapReadEmail, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.jakartaUtils.connectStore(store, authentication);
      Folder defaultFolder = store.getDefaultFolder();
      String targetFolder = imapReadEmail.readEmailFolder();
      try (Folder imapFolder = this.jakartaUtils.findImapFolder(defaultFolder, targetFolder)) {
        imapFolder.open(Folder.READ_ONLY);
        Message[] messages = imapFolder.search(new MessageIDTerm(imapReadEmail.messageId()));
        return Arrays.stream(messages)
            .findFirst()
            .map(this.jakartaUtils::createEmail)
            .map(
                email ->
                    new ReadEmailResponse(
                        email.messageId(),
                        email.from(),
                        email.headers(),
                        email.subject(),
                        email.size(),
                        email.body().bodyAsPlainText(),
                        email.body().bodyAsHtml(),
                        email.receivedAt()))
            .orElseThrow(() -> new MessagingException("Could not find an email ID"));
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private DeleteEmailResponse imapDeleteEmail(
      ImapDeleteEmail imapDeleteEmail, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.jakartaUtils.connectStore(store, authentication);
      Folder defaultFolder = store.getDefaultFolder();
      String targetFolder = imapDeleteEmail.deleteEmailFolder();
      try (Folder folder = this.jakartaUtils.findImapFolder(defaultFolder, targetFolder)) {
        return deleteEmail(folder, imapDeleteEmail.messageId());
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private MoveEmailResponse imapMoveEmails(
      ImapMoveEmail imapMoveEmail, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.jakartaUtils.connectStore(store, authentication);
      Folder rootFolder = store.getDefaultFolder();
      String fromFolder = imapMoveEmail.fromFolder();
      String toFolder = imapMoveEmail.toFolder();
      Folder sourceImapFolder = this.jakartaUtils.findImapFolder(rootFolder, fromFolder);
      if (!sourceImapFolder.exists()) throw new MessagingException("Source folder does not exist");
      sourceImapFolder.open(Folder.READ_WRITE);
      Folder targetImapFolder =
          store.getFolder(
              String.join(String.valueOf(rootFolder.getSeparator()), toFolder.split("\\.")));
      if (!targetImapFolder.exists()) targetImapFolder.create(Folder.HOLDS_MESSAGES);
      targetImapFolder.open(Folder.READ_WRITE);

      Message[] messages = sourceImapFolder.search(new MessageIDTerm(imapMoveEmail.messageId()));
      Message message =
          Arrays.stream(messages)
              .findFirst()
              .orElseThrow(
                  () ->
                      new MessagingException(
                          "Email with messageId %s does not exist"
                              .formatted(imapMoveEmail.messageId())));
      sourceImapFolder.copyMessages(new Message[] {message}, targetImapFolder);
      this.jakartaUtils.markAsDeleted(message);
      sourceImapFolder.close();
      targetImapFolder.close();
      return new MoveEmailResponse(
          imapMoveEmail.messageId(), imapMoveEmail.fromFolder(), imapMoveEmail.toFolder());
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private List<ListEmailsResponse> imapListEmails(
      ImapListEmails imapListEmails, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.jakartaUtils.connectStore(store, authentication);
      Folder rootFolder = store.getDefaultFolder();
      String targetFolder = imapListEmails.listEmailsFolder();
      try (Folder imapFolder = this.jakartaUtils.findImapFolder(rootFolder, targetFolder)) {
        imapFolder.open(Folder.READ_ONLY);
        return Arrays.stream(imapFolder.getMessages())
            .map(this.jakartaUtils::createBodylessEmail)
            .sorted(
                this.jakartaUtils.retrieveEmailComparator(
                    imapListEmails.sortField(), imapListEmails.sortOrder()))
            .map(
                email ->
                    new ListEmailsResponse(
                        email.messageId(), email.from(), email.subject(), email.size()))
            .limit(imapListEmails.maxToBeRead())
            .toList();
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private DeleteEmailResponse pop3DeleteEmail(
      Pop3DeleteEmail pop3DeleteEmail, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.jakartaUtils.connectStore(store, authentication);
      try (Folder folder = store.getFolder("INBOX")) {
        return deleteEmail(folder, pop3DeleteEmail.messageId());
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private ReadEmailResponse pop3ReadEmail(
      Pop3ReadEmail pop3ReadEmail, Authentication authentication, Session session) {
    try {
      try (Store store = session.getStore()) {
        this.jakartaUtils.connectStore(store, authentication);
        try (Folder folder = store.getFolder("INBOX")) {
          folder.open(Folder.READ_WRITE);
          Message[] messages = folder.search(new MessageIDTerm(pop3ReadEmail.messageId()));
          return Arrays.stream(messages)
              .findFirst()
              .map(this.jakartaUtils::createEmail)
              .map(
                  email ->
                      new ReadEmailResponse(
                          email.messageId(),
                          email.from(),
                          email.headers(),
                          email.subject(),
                          email.size(),
                          email.body().bodyAsPlainText(),
                          email.body().bodyAsHtml(),
                          email.receivedAt()))
              .orElseThrow(() -> new MessagingException("No emails have been found with this ID"));
        }
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private List<ListEmailsResponse> pop3ListEmails(
      Pop3ListEmails pop3ListEmails, Authentication authentication, Session session) {
    try {
      try (Store store = session.getStore()) {
        this.jakartaUtils.connectStore(store, authentication);
        try (Folder folder = store.getFolder("INBOX")) {
          folder.open(Folder.READ_ONLY);
          return Arrays.stream(folder.getMessages())
              .map(this.jakartaUtils::createBodylessEmail)
              .sorted(
                  this.jakartaUtils.retrieveEmailComparator(
                      pop3ListEmails.sortField(), pop3ListEmails.sortOrder()))
              .map(
                  email ->
                      new ListEmailsResponse(
                          email.messageId(), email.from(), email.subject(), email.size()))
              .limit(pop3ListEmails.maxToBeRead())
              .toList();
        }
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private List<SearchEmailsResponse> pop3SearchEmails(
      Pop3SearchEmails pop3SearchEmails, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.jakartaUtils.connectStore(store, authentication);
      try (Folder folder = store.getFolder("INBOX")) {
        return searchEmails(folder, pop3SearchEmails.criteria());
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private SendEmailResponse smtpSendEmail(
      SmtpSendEmail smtpSendEmail, Authentication authentication, Session session) {
    try {
      Optional<InternetAddress[]> to = createParsedInternetAddresses(smtpSendEmail.to());
      Optional<InternetAddress[]> cc = createParsedInternetAddresses(smtpSendEmail.cc());
      Optional<InternetAddress[]> bcc = createParsedInternetAddresses(smtpSendEmail.bcc());
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(smtpSendEmail.from()));
      if (to.isPresent()) message.setRecipients(Message.RecipientType.TO, to.get());
      if (cc.isPresent()) message.setRecipients(Message.RecipientType.CC, cc.get());
      if (bcc.isPresent()) message.setRecipients(Message.RecipientType.BCC, bcc.get());
      message.setSubject(smtpSendEmail.subject());
      message.setText(smtpSendEmail.body());
      try (Transport transport = session.getTransport()) {
        this.jakartaUtils.connectTransport(transport, authentication);
        transport.sendMessage(message, message.getAllRecipients());
      }
      return new SendEmailResponse(smtpSendEmail.subject(), true);
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private SearchTerm createSearchTerms(JsonNode jsonNode) throws AddressException {
    List<SearchTerm> searchTerms = new ArrayList<>();
    if (jsonNode.has("operator")) {
      JsonNode criteriaArray = jsonNode.get("criteria");
      for (JsonNode criteria : criteriaArray) {
        searchTerms.add(createSearchTerms(criteria));
      }
    } else {
      return switch (SearchCriteria.valueOf(jsonNode.get("field").asText())) {
        case FROM -> new FromTerm(new InternetAddress(jsonNode.get("value").asText()));
        case SUBJECT -> new SubjectTerm(jsonNode.get("value").asText());
        case BODY -> new BodyTerm(jsonNode.get("value").asText());
      };
    }
    return switch (SearchOperator.valueOf(jsonNode.get("operator").asText())) {
      case AND -> new AndTerm(searchTerms.toArray(new SearchTerm[0]));
      case OR -> new OrTerm(searchTerms.toArray(new SearchTerm[0]));
    };
  }

  private List<SearchEmailsResponse> searchEmails(Folder folder, Object criteria)
      throws MessagingException {
    folder.open(Folder.READ_ONLY);
    JsonNode jsonNode = this.objectMapper.convertValue(criteria, JsonNode.class);
    SearchTerm searchTerm = createSearchTerms(jsonNode);
    return Arrays.stream(folder.search(searchTerm, folder.getMessages()))
        .map(this.jakartaUtils::createBodylessEmail)
        .map(email -> new SearchEmailsResponse(email.messageId(), email.subject()))
        .toList();
  }

  private DeleteEmailResponse deleteEmail(Folder folder, String messageId)
      throws MessagingException {
    folder.open(Folder.READ_WRITE);
    Message[] messages = folder.search(new MessageIDTerm(messageId));
    Message message =
        Arrays.stream(messages)
            .findFirst()
            .orElseThrow(() -> new MessagingException("No emails have been found with this ID"));
    this.jakartaUtils.markAsDeleted(message);
    return new DeleteEmailResponse(messageId, true);
  }

  private Optional<InternetAddress[]> createParsedInternetAddresses(Object object)
      throws AddressException {
    if (Objects.isNull(object)) {
      return Optional.empty();
    }
    return Optional.of(
        switch (object) {
          case List<?> list ->
              InternetAddress.parse(String.join(",", list.stream().map(Object::toString).toList()));
          case String string -> InternetAddress.parse(string);
          default ->
              throw new IllegalStateException(
                  "Unexpected value: " + object + ". List or String was expected");
        });
  }
}
