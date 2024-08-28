/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core.jakarta;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.core.ActionExecutor;
import io.camunda.connector.email.outbound.model.EmailRequest;
import io.camunda.connector.email.outbound.protocols.Protocol;
import io.camunda.connector.email.outbound.protocols.actions.*;
import io.camunda.connector.email.response.ListEmailsResponse;
import io.camunda.connector.email.response.ReadEmailResponse;
import jakarta.mail.*;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.*;
import java.util.*;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.pop3.POP3Folder;

public class JakartaActionExecutor implements ActionExecutor {

  private final JakartaUtils sessionFactory;

  private JakartaActionExecutor(JakartaUtils sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  public static JakartaActionExecutor create(JakartaUtils sessionFactory) {
    return new JakartaActionExecutor(sessionFactory);
  }

  public Object execute(EmailRequest emailRequest) {
    Authentication authentication = emailRequest.getAuthentication();
    Protocol protocol = emailRequest.getData();
    Action action = protocol.getProtocolAction();
    Session session = sessionFactory.createSession(protocol.getConfiguration(), authentication);
    return switch (action) {
      case SmtpSendEmail smtpSendEmail -> smtpSendEmail(smtpSendEmail, authentication, session);
      case ImapMoveEmails imapMoveEmails -> imapMoveEmails(imapMoveEmails, authentication, session);
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
    };
  }

  private Object imapSearchEmails(
      ImapSearchEmails imapSearchEmails, Authentication authentication, Session session) {
    return null;
  }

  private Object imapReadEmail(
      ImapReadEmail imapReadEmail, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.sessionFactory.connectStore(store, authentication);
      try (IMAPFolder imapFolder = (IMAPFolder) store.getDefaultFolder()) {
        Message[] messages = imapFolder.search(new MessageIDTerm(imapReadEmail.getMessageId()));
        return Arrays.stream(messages)
            .findFirst()
            .map(Email::createEmail)
            .map(
                email ->
                    new ReadEmailResponse(
                        email.getMessageId(),
                        email.getFrom(),
                        email.getSubject(),
                        email.getSize(),
                        email.getBody().getBodyAsPlainText(),
                        email.getBody().getBodyAsHtml()))
            .orElseThrow(() -> new MessagingException("Could not find an email ID"));
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object imapDeleteEmail(
      ImapDeleteEmail imapDeleteEmail, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.sessionFactory.connectStore(store, authentication);
      try (IMAPFolder imapFolder = (IMAPFolder) store.getDefaultFolder()) {
        Message[] messages = imapFolder.search(new MessageIDTerm(imapDeleteEmail.getMessageId()));
        Arrays.stream(messages)
            .findFirst()
            .ifPresentOrElse(
                this.sessionFactory::markAsDeleted,
                () -> {
                  throw new RuntimeException("No emails have been found with this ID");
                });
        return true;
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object imapMoveEmails(
      ImapMoveEmails imapMoveEmails, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.sessionFactory.connectStore(store, authentication);
      IMAPFolder sourceImapFolder = (IMAPFolder) store.getFolder(imapMoveEmails.getFromFolder());
      if (!sourceImapFolder.exists()) throw new MessagingException("Source folder does not exist");
      sourceImapFolder.open(Folder.READ_WRITE);
      IMAPFolder targetImapFolder = (IMAPFolder) store.getFolder(imapMoveEmails.getToFolder());
      if (!targetImapFolder.exists()) targetImapFolder.create(Folder.HOLDS_MESSAGES);
      targetImapFolder.open(Folder.READ_WRITE);

      Message[] messages =
          sourceImapFolder.search(new MessageIDTerm(imapMoveEmails.getMessageId()));
      sourceImapFolder.copyMessages(messages, targetImapFolder);
      sourceImapFolder.setFlags(messages, new Flags(Flags.Flag.DELETED), true);

      sourceImapFolder.close();
      targetImapFolder.close();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  private Object imapListEmails(
      ImapListEmails imapListEmails, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.sessionFactory.connectStore(store, authentication);
      try (IMAPFolder imapFolder =
          (IMAPFolder)
              (imapListEmails.getListEmailsFolder().isEmpty()
                  ? store.getDefaultFolder()
                  : store.getFolder(imapListEmails.getListEmailsFolder().get()))) {
        imapFolder.open(Folder.READ_ONLY);
        Message[] messages = imapFolder.getMessages(1, imapListEmails.getMaxToBeRead());
        return Arrays.stream(messages)
            .map(Email::createBodylessEmail)
            .sorted(
                this.sessionFactory.retrieveEmailComparator(
                    imapListEmails.getSortField(), imapListEmails.getSortOrder()))
            .map(
                email ->
                    new ListEmailsResponse(
                        email.getMessageId(),
                        email.getFrom(),
                        email.getSubject(),
                        email.getSize()));
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object pop3DeleteEmail(
      Pop3DeleteEmail pop3DeleteEmail, Authentication authentication, Session session) {
    try (Store store = session.getStore()) {
      this.sessionFactory.connectStore(store, authentication);
      try (POP3Folder folder = (POP3Folder) store.getDefaultFolder()) {
        folder.open(Folder.READ_WRITE);
        Message[] messages = folder.search(new MessageIDTerm(pop3DeleteEmail.getMessageId()));
        Arrays.stream(messages)
            .findFirst()
            .ifPresentOrElse(
                this.sessionFactory::markAsDeleted,
                () -> {
                  throw new RuntimeException("No emails have been found with this ID");
                });
        return true;
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object pop3ReadEmail(
      Pop3ReadEmail pop3ReadEmail, Authentication authentication, Session session) {
    try {
      try (Store store = session.getStore()) {
        this.sessionFactory.connectStore(store, authentication);
        try (POP3Folder folder = (POP3Folder) store.getDefaultFolder()) {
          folder.open(Folder.READ_WRITE);
          Message[] messages = folder.search(new MessageIDTerm(pop3ReadEmail.getMessageId()));
          return Arrays.stream(messages)
              .findFirst()
              .map(Email::createEmail)
              .map(
                  email ->
                      new ReadEmailResponse(
                          email.getMessageId(),
                          email.getFrom(),
                          email.getSubject(),
                          email.getSize(),
                          email.getBody().getBodyAsPlainText(),
                          email.getBody().getBodyAsHtml()))
              .orElseThrow(() -> new MessagingException("No emails have been found with this ID"));
        }
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object pop3ListEmails(
      Pop3ListEmails pop3ListEmails, Authentication authentication, Session session) {
    try {
      try (Store store = session.getStore()) {
        this.sessionFactory.connectStore(store, authentication);
        try (POP3Folder folder = (POP3Folder) store.getDefaultFolder()) {
          folder.open(Folder.READ_ONLY);
          Message[] messages = folder.getMessages(1, pop3ListEmails.getMaxToBeRead());
          return Arrays.stream(messages)
              .map(Email::createBodylessEmail)
              .map(
                  email ->
                      new ListEmailsResponse(
                          email.getMessageId(),
                          email.getFrom(),
                          email.getSubject(),
                          email.getSize()))
              .toList();
        }
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean smtpSendEmail(
      SmtpSendEmail smtpSendEmail, Authentication authentication, Session session) {
    try {
      Optional<InternetAddress[]> to = createParsedInternetAddresses(smtpSendEmail.getTo());
      Optional<InternetAddress[]> cc = createParsedInternetAddresses(smtpSendEmail.getCc());
      Optional<InternetAddress[]> cci = createParsedInternetAddresses(smtpSendEmail.getCci());
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(authentication.getSender()));
      if (to.isPresent()) message.setRecipients(Message.RecipientType.TO, to.get());
      if (cc.isPresent()) message.setRecipients(Message.RecipientType.CC, cc.get());
      if (cci.isPresent()) message.setRecipients(Message.RecipientType.BCC, cci.get());
      message.setSubject(smtpSendEmail.getSubject());
      message.setText(smtpSendEmail.getBody());
      try (Transport transport = session.getTransport()) {
        this.sessionFactory.connectTransport(transport, authentication);
        transport.sendMessage(message, message.getAllRecipients());
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
    return true;
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
