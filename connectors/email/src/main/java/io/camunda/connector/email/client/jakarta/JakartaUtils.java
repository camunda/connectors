/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.config.Pop3Config;
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.email.outbound.protocols.actions.SortFieldImap;
import io.camunda.connector.email.outbound.protocols.actions.SortFieldPop3;
import io.camunda.connector.email.outbound.protocols.actions.SortOrder;
import jakarta.mail.*;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Properties;

public class JakartaUtils {
  public Session createSession(Configuration configuration, Authentication authentication) {
    return Session.getInstance(
        switch (configuration) {
          case ImapConfig imap -> createProperties(imap, authentication.isSecuredAuth());
          case Pop3Config pop3 -> createProperties(pop3, authentication.isSecuredAuth());
          case SmtpConfig smtp -> createProperties(smtp, authentication.isSecuredAuth());
        });
  }

  public void connectStore(Store store, Authentication authentication) throws MessagingException {
    if (authentication.isSecuredAuth())
      store.connect(
          authentication.getUser().orElseThrow(() -> new RuntimeException("Unexpected Error")),
          authentication.getSecret().orElseThrow(() -> new RuntimeException("Unexpected Error")));
    else store.connect();
  }

  public void connectTransport(Transport transport, Authentication authentication)
      throws MessagingException {
    if (authentication.isSecuredAuth())
      transport.connect(
          authentication.getUser().orElseThrow(() -> new RuntimeException("Unexpected Error")),
          authentication.getSecret().orElseThrow(() -> new RuntimeException("Unexpected Error")));
    else transport.connect();
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

  private Properties createProperties(SmtpConfig smtp, Boolean securedAuth) {
    Properties properties = new Properties();
    properties.put("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.host", smtp.smtpHost());
    properties.put("mail.smtp.port", smtp.smtpPort().toString());
    properties.put("mail.smtp.auth", securedAuth);
    switch (smtp.smtpCryptographicProtocol()) {
      case NONE -> {}
      case TLS -> properties.put("mail.smtp.starttls.enable", true);
      case SSL -> properties.put("mail.smtp.ssl.enable", true);
    }
    return properties;
  }

  private Properties createProperties(Pop3Config pop3, Boolean securedAuth) {
    Properties properties = new Properties();

    switch (pop3.pop3CryptographicProtocol()) {
      case NONE -> {
        properties.put("mail.store.protocol", "pop3");
        properties.put("mail.pop3.host", pop3.pop3Host());
        properties.put("mail.pop3.port", pop3.pop3Port().toString());
        properties.put("mail.pop3.auth", securedAuth);
      }
      case TLS -> {
        properties.put("mail.store.protocol", "pop3s");
        properties.put("mail.pop3s.host", pop3.pop3Host());
        properties.put("mail.pop3s.port", pop3.pop3Port().toString());
        properties.put("mail.pop3s.auth", securedAuth);
        properties.put("mail.pop3s.starttls.enable", true);
      }
      case SSL -> {
        properties.put("mail.store.protocol", "pop3s");
        properties.put("mail.pop3s.host", pop3.pop3Host());
        properties.put("mail.pop3s.port", pop3.pop3Port().toString());
        properties.put("mail.pop3s.auth", securedAuth);
        properties.put("mail.pop3s.ssl.enable", true);
      }
    }
    return properties;
  }

  private Properties createProperties(ImapConfig imap, Boolean securedAuth) {
    Properties properties = new Properties();

    switch (imap.imapCryptographicProtocol()) {
      case NONE -> {
        properties.put("mail.store.protocol", "imap");
        properties.put("mail.imap.host", imap.imapHost());
        properties.put("mail.imap.port", imap.imapPort().toString());
        properties.put("mail.imap.auth", securedAuth);
      }
      case TLS -> {
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", imap.imapHost());
        properties.put("mail.imaps.port", imap.imapPort().toString());
        properties.put("mail.imaps.auth", securedAuth);
        properties.put("mail.imaps.starttls.enable", true);
        properties.put("mail.imaps.usesocketchannels", true);
      }
      case SSL -> {
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", imap.imapHost());
        properties.put("mail.imaps.port", imap.imapPort().toString());
        properties.put("mail.imaps.auth", securedAuth);
        properties.put("mail.imaps.ssl.enable", true);
        properties.put("mail.imaps.usesocketchannel", true);
      }
    }
    return properties;
  }

  public Comparator<Email> retrieveEmailComparator(
      @NotNull SortFieldPop3 sortFieldPop3, @NotNull SortOrder sortOrder) {
    return (email1, email2) ->
        switch (sortFieldPop3) {
          case SENT_DATE -> sortOrder.order(email1.getSentAt().compareTo(email2.getSentAt()));
          case SIZE -> sortOrder.order(email1.getSize().compareTo(email2.getSize()));
        };
  }

  public Comparator<Email> retrieveEmailComparator(
      @NotNull SortFieldImap sortFieldImap, @NotNull SortOrder sortOrder) {
    return (email1, email2) ->
        switch (sortFieldImap) {
          case RECEIVED_DATE ->
              sortOrder.order(email1.getReceivedAt().compareTo(email2.getReceivedAt()));
          case SENT_DATE -> sortOrder.order(email1.getSentAt().compareTo(email2.getSentAt()));
          case SIZE -> sortOrder.order(email1.getSize().compareTo(email2.getSize()));
        };
  }

  private Folder findFolderRecursively(Folder rootFolder, String targetFolder)
      throws MessagingException {
    if (targetFolder == null || targetFolder.isEmpty() || "INBOX".equals(targetFolder)) {
      return rootFolder.getFolder("INBOX");
    }
    Folder[] folders = rootFolder.list();
    for (Folder folder : folders) {
      if (folder.getName().equals(targetFolder)) {
        return folder;
      } else {
        Folder folderReturned = findFolderRecursively(folder, targetFolder);
        if (folderReturned != null) {
          return folderReturned;
        }
      }
    }
    return null;
  }

  public Folder findImapFolder(Folder rootFolder, String targetFolder) throws MessagingException {
    Folder folder = findFolderRecursively(rootFolder, targetFolder);
    if (folder != null) {
      return folder;
    }
    throw new MessagingException("Unable to find IMAP folder");
  }
}
