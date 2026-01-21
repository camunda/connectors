/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.client.EmailActionExecutor;
import io.camunda.connector.email.client.jakarta.models.EmailAttachment;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.outbound.model.EmailRequest;
import io.camunda.connector.email.outbound.protocols.Protocol;
import io.camunda.connector.email.outbound.protocols.actions.*;
import io.camunda.connector.email.response.*;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.*;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JakartaEmailActionExecutor implements EmailActionExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(JakartaEmailActionExecutor.class);

  private final JakartaUtils jakartaUtils;
  private final ObjectMapper objectMapper;
  private OutboundConnectorContext connectorContext;

  private JakartaEmailActionExecutor(JakartaUtils jakartaUtils, ObjectMapper objectMapper) {
    this.jakartaUtils = jakartaUtils;
    this.objectMapper = objectMapper;
    LOG.debug("JakartaEmailActionExecutor instance created");
  }

  public static JakartaEmailActionExecutor create(
      JakartaUtils sessionFactory, ObjectMapper objectMapper) {
    LOG.debug("Creating JakartaEmailActionExecutor with JakartaUtils and ObjectMapper");
    return new JakartaEmailActionExecutor(sessionFactory, objectMapper);
  }

  public Object execute(OutboundConnectorContext context) {
    LOG.debug("Starting execute method");
    this.connectorContext = context;
    EmailRequest emailRequest = context.bindVariables(EmailRequest.class);
    LOG.debug("Bound email request variables successfully");
    Authentication authentication = emailRequest.authentication();
    Protocol protocol = emailRequest.data();
    Action action = protocol.getProtocolAction();
    LOG.debug("Executing action: {}", action.getClass().getSimpleName());
    Session session = jakartaUtils.createSession(protocol.getConfiguration(), authentication);
    LOG.debug("Mail session created successfully");
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
    LOG.debug("Starting IMAP search emails operation");
    try (Store store = session.getStore()) {
      LOG.debug("Connecting to IMAP store");
      this.jakartaUtils.connectStore(store, authentication);
      String targetFolder = imapSearchEmails.searchEmailFolder();
      LOG.debug("Searching in folder: {}", targetFolder);
      try (Folder imapFolder = this.jakartaUtils.findImapFolder(store, targetFolder)) {
        LOG.debug("IMAP folder found, executing search");
        List<SearchEmailsResponse> results = searchEmails(imapFolder, imapSearchEmails.criteria());
        LOG.debug("IMAP search completed, found {} emails", results.size());
        return results;
      }
    } catch (MessagingException e) {
      LOG.debug("IMAP search emails failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private ReadEmailResponse imapReadEmail(
      ImapReadEmail imapReadEmail, Authentication authentication, Session session) {
    LOG.debug("Starting IMAP read email operation for messageId: {}", imapReadEmail.messageId());
    try (Store store = session.getStore()) {
      LOG.debug("Connecting to IMAP store");
      this.jakartaUtils.connectStore(store, authentication);
      String targetFolder = imapReadEmail.readEmailFolder();
      LOG.debug("Reading from folder: {}", targetFolder);
      try (Folder imapFolder = this.jakartaUtils.findImapFolder(store, targetFolder)) {
        imapFolder.open(Folder.READ_ONLY);
        LOG.debug("Folder opened in READ_ONLY mode, searching for message");
        Message[] messages = imapFolder.search(new MessageIDTerm(imapReadEmail.messageId()));
        LOG.debug("Search returned {} message(s)", messages.length);
        return Arrays.stream(messages)
            .findFirst()
            .map(this.jakartaUtils::createEmail)
            .map(
                email -> {
                  LOG.debug("Email found - size: {}", email.size());
                  return new ReadEmailResponse(
                      email.messageId(),
                      email.from(),
                      email.headers(),
                      email.subject(),
                      email.size(),
                      email.body().bodyAsPlainText(),
                      email.body().bodyAsHtml(),
                      this.createDocumentList(email.body().attachments(), connectorContext),
                      email.receivedAt());
                })
            .orElseThrow(
                () -> {
                  LOG.debug("Email with messageId {} not found", imapReadEmail.messageId());
                  return new MessagingException("Could not find an email ID");
                });
      }
    } catch (MessagingException e) {
      LOG.debug("IMAP read email failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private DeleteEmailResponse imapDeleteEmail(
      ImapDeleteEmail imapDeleteEmail, Authentication authentication, Session session) {
    LOG.debug(
        "Starting IMAP delete email operation for messageId: {}", imapDeleteEmail.messageId());
    try (Store store = session.getStore()) {
      LOG.debug("Connecting to IMAP store");
      this.jakartaUtils.connectStore(store, authentication);
      String targetFolder = imapDeleteEmail.deleteEmailFolder();
      LOG.debug("Deleting from folder: {}", targetFolder);
      try (Folder folder = this.jakartaUtils.findImapFolder(store, targetFolder)) {
        DeleteEmailResponse response = deleteEmail(folder, imapDeleteEmail.messageId());
        LOG.debug(
            "IMAP delete email completed successfully for messageId: {}",
            imapDeleteEmail.messageId());
        return response;
      }
    } catch (MessagingException e) {
      LOG.debug("IMAP delete email failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private MoveEmailResponse imapMoveEmails(
      ImapMoveEmail imapMoveEmail, Authentication authentication, Session session) {
    LOG.debug(
        "Starting IMAP move email operation for messageId: {} from '{}' to '{}'",
        imapMoveEmail.messageId(),
        imapMoveEmail.fromFolder(),
        imapMoveEmail.toFolder());
    try (Store store = session.getStore()) {
      LOG.debug("Connecting to IMAP store");
      this.jakartaUtils.connectStore(store, authentication);
      String fromFolder = imapMoveEmail.fromFolder();
      Folder sourceImapFolder = this.jakartaUtils.findImapFolder(store, fromFolder);
      sourceImapFolder.open(Folder.READ_WRITE);
      LOG.debug("Source folder '{}' opened in READ_WRITE mode", fromFolder);
      Message[] messages = sourceImapFolder.search(new MessageIDTerm(imapMoveEmail.messageId()));
      LOG.debug("Search returned {} message(s)", messages.length);
      Message message =
          Arrays.stream(messages)
              .findFirst()
              .orElseThrow(
                  () -> {
                    LOG.debug(
                        "Email with messageId {} does not exist in folder {}",
                        imapMoveEmail.messageId(),
                        fromFolder);
                    return new MessagingException(
                        "Email with messageId %s does not exist"
                            .formatted(imapMoveEmail.messageId()));
                  });
      LOG.debug("Moving message to folder: {}", imapMoveEmail.toFolder());
      this.jakartaUtils.moveMessage(store, message, imapMoveEmail.toFolder());
      sourceImapFolder.close();
      LOG.debug("IMAP move email completed successfully");
      return new MoveEmailResponse(
          imapMoveEmail.messageId(), imapMoveEmail.fromFolder(), imapMoveEmail.toFolder());
    } catch (MessagingException e) {
      LOG.debug("IMAP move email failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private List<ListEmailsResponse> imapListEmails(
      ImapListEmails imapListEmails, Authentication authentication, Session session) {
    LOG.debug(
        "Starting IMAP list emails operation, maxToBeRead: {}, sortField: {}, sortOrder: {}",
        imapListEmails.maxToBeRead(),
        imapListEmails.sortField(),
        imapListEmails.sortOrder());
    try (Store store = session.getStore()) {
      LOG.debug("Connecting to IMAP store");
      this.jakartaUtils.connectStore(store, authentication);
      String targetFolder = imapListEmails.listEmailsFolder();
      LOG.debug("Listing emails from folder: {}", targetFolder);
      try (Folder imapFolder = this.jakartaUtils.findImapFolder(store, targetFolder)) {
        imapFolder.open(Folder.READ_ONLY);
        int totalMessages = imapFolder.getMessageCount();
        LOG.debug("Folder opened, total messages in folder: {}", totalMessages);
        List<ListEmailsResponse> results =
            Arrays.stream(imapFolder.getMessages())
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
        LOG.debug("IMAP list emails completed, returning {} emails", results.size());
        return results;
      }
    } catch (MessagingException e) {
      LOG.debug("IMAP list emails failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private DeleteEmailResponse pop3DeleteEmail(
      Pop3DeleteEmail pop3DeleteEmail, Authentication authentication, Session session) {
    LOG.debug(
        "Starting POP3 delete email operation for messageId: {}", pop3DeleteEmail.messageId());
    try (Store store = session.getStore()) {
      LOG.debug("Connecting to POP3 store");
      this.jakartaUtils.connectStore(store, authentication);
      try (Folder folder = store.getFolder("INBOX")) {
        LOG.debug("Accessing INBOX folder");
        DeleteEmailResponse response = deleteEmail(folder, pop3DeleteEmail.messageId());
        LOG.debug(
            "POP3 delete email completed successfully for messageId: {}",
            pop3DeleteEmail.messageId());
        return response;
      }
    } catch (MessagingException e) {
      LOG.debug("POP3 delete email failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private ReadEmailResponse pop3ReadEmail(
      Pop3ReadEmail pop3ReadEmail, Authentication authentication, Session session) {
    LOG.debug("Starting POP3 read email operation for messageId: {}", pop3ReadEmail.messageId());
    try {
      try (Store store = session.getStore()) {
        LOG.debug("Connecting to POP3 store");
        this.jakartaUtils.connectStore(store, authentication);
        try (Folder folder = store.getFolder("INBOX")) {
          folder.open(Folder.READ_WRITE);
          LOG.debug("INBOX folder opened in READ_WRITE mode, searching for message");
          Message[] messages = folder.search(new MessageIDTerm(pop3ReadEmail.messageId()));
          LOG.debug("Search returned {} message(s)", messages.length);
          return Arrays.stream(messages)
              .findFirst()
              .map(this.jakartaUtils::createEmail)
              .map(
                  email -> {
                    LOG.debug("Email found - size: {}", email.size());
                    return new ReadEmailResponse(
                        email.messageId(),
                        email.from(),
                        email.headers(),
                        email.subject(),
                        email.size(),
                        email.body().bodyAsPlainText(),
                        email.body().bodyAsHtml(),
                        this.createDocumentList(email.body().attachments(), this.connectorContext),
                        email.receivedAt());
                  })
              .orElseThrow(
                  () -> {
                    LOG.debug("Email with messageId {} not found", pop3ReadEmail.messageId());
                    return new MessagingException("No emails have been found with this ID");
                  });
        }
      }
    } catch (MessagingException e) {
      LOG.debug("POP3 read email failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private List<ListEmailsResponse> pop3ListEmails(
      Pop3ListEmails pop3ListEmails, Authentication authentication, Session session) {
    LOG.debug(
        "Starting POP3 list emails operation, maxToBeRead: {}, sortField: {}, sortOrder: {}",
        pop3ListEmails.maxToBeRead(),
        pop3ListEmails.sortField(),
        pop3ListEmails.sortOrder());
    try {
      try (Store store = session.getStore()) {
        LOG.debug("Connecting to POP3 store");
        this.jakartaUtils.connectStore(store, authentication);
        try (Folder folder = store.getFolder("INBOX")) {
          folder.open(Folder.READ_ONLY);
          int totalMessages = folder.getMessageCount();
          LOG.debug("INBOX folder opened, total messages: {}", totalMessages);
          List<ListEmailsResponse> results =
              Arrays.stream(folder.getMessages())
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
          LOG.debug("POP3 list emails completed, returning {} emails", results.size());
          return results;
        }
      }
    } catch (MessagingException e) {
      LOG.debug("POP3 list emails failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private List<SearchEmailsResponse> pop3SearchEmails(
      Pop3SearchEmails pop3SearchEmails, Authentication authentication, Session session) {
    LOG.debug("Starting POP3 search emails operation");
    try (Store store = session.getStore()) {
      LOG.debug("Connecting to POP3 store");
      this.jakartaUtils.connectStore(store, authentication);
      try (Folder folder = store.getFolder("INBOX")) {
        LOG.debug("Accessing INBOX folder for search");
        List<SearchEmailsResponse> results = searchEmails(folder, pop3SearchEmails.criteria());
        LOG.debug("POP3 search emails completed, found {} emails", results.size());
        return results;
      }
    } catch (MessagingException e) {
      LOG.debug("POP3 search emails failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private SendEmailResponse smtpSendEmail(
      SmtpSendEmail smtpSendEmail, Authentication authentication, Session session) {
    LOG.debug("Starting SMTP send email operation - contentType: {}", smtpSendEmail.contentType());
    try {
      Optional<InternetAddress[]> to = createParsedInternetAddresses(smtpSendEmail.to());
      Optional<InternetAddress[]> cc = createParsedInternetAddresses(smtpSendEmail.cc());
      Optional<InternetAddress[]> bcc = createParsedInternetAddresses(smtpSendEmail.bcc());
      LOG.debug(
          "Recipients parsed - TO: {}, CC: {}, BCC: {}",
          to.map(a -> a.length).orElse(0),
          cc.map(a -> a.length).orElse(0),
          bcc.map(a -> a.length).orElse(0));
      Optional<Map<String, String>> headers = Optional.ofNullable(smtpSendEmail.headers());
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(smtpSendEmail.from()));
      if (to.isPresent()) message.setRecipients(Message.RecipientType.TO, to.get());
      if (cc.isPresent()) message.setRecipients(Message.RecipientType.CC, cc.get());
      if (bcc.isPresent()) message.setRecipients(Message.RecipientType.BCC, bcc.get());
      headers.ifPresent(
          stringObjectMap -> {
            LOG.debug("Setting {} custom headers", stringObjectMap.size());
            setMessageHeaders(stringObjectMap, message);
          });
      message.setSubject(smtpSendEmail.subject());
      LOG.debug("Building multipart content for content type: {}", smtpSendEmail.contentType());
      Multipart multipart = getMultipart(smtpSendEmail);
      if (!Objects.isNull(smtpSendEmail.attachments())) {
        LOG.debug("Adding {} attachment(s) to email", smtpSendEmail.attachments().size());
        smtpSendEmail.attachments().forEach(getDocumentConsumer(multipart));
      }
      message.setContent(multipart);
      LOG.debug("Message prepared, connecting to SMTP transport");
      try (Transport transport = session.getTransport()) {
        this.jakartaUtils.connectTransport(transport, authentication);
        LOG.debug(
            "SMTP transport connected, sending message to {} recipient(s)",
            message.getAllRecipients().length);
        transport.sendMessage(message, message.getAllRecipients());
      }
      LOG.debug("SMTP send email completed successfully");
      return new SendEmailResponse(smtpSendEmail.subject(), true, message.getMessageID());
    } catch (MessagingException e) {
      LOG.debug("SMTP send email failed with error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private void setMessageHeaders(Map<String, String> stringObjectMap, Message message) {
    LOG.debug("Setting {} message headers", stringObjectMap.size());
    stringObjectMap.forEach(
        (key, value) -> {
          try {
            message.setHeader(key, value);
          } catch (MessagingException e) {
            LOG.debug("Failed to set a header: {}", e.getMessage());
            throw new RuntimeException(e);
          }
        });
  }

  private Multipart getMultipart(SmtpSendEmail smtpSendEmail) throws MessagingException {
    LOG.debug("Creating multipart for content type: {}", smtpSendEmail.contentType());
    Multipart multipart = new MimeMultipart();
    switch (smtpSendEmail.contentType()) {
      case PLAIN -> {
        LOG.debug("Adding plain text body part");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(smtpSendEmail.body(), StandardCharsets.UTF_8.name());
        multipart.addBodyPart(textPart);
      }
      case HTML -> {
        LOG.debug("Adding HTML body part");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(smtpSendEmail.htmlBody(), JakartaUtils.HTML_CHARSET);
        multipart.addBodyPart(htmlPart);
      }
      case MULTIPART -> {
        LOG.debug("Adding both plain text and HTML body parts");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(smtpSendEmail.body(), StandardCharsets.UTF_8.name());
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(smtpSendEmail.htmlBody(), JakartaUtils.HTML_CHARSET);
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(htmlPart);
      }
    }
    LOG.debug("Multipart created with {} body part(s)", multipart.getCount());
    return multipart;
  }

  private SearchTerm createSearchTerms(JsonNode jsonNode) throws AddressException {
    LOG.debug("Creating search terms from JSON node");
    List<SearchTerm> searchTerms = new ArrayList<>();
    if (jsonNode.has("operator")) {
      JsonNode criteriaArray = jsonNode.get("criteria");
      LOG.debug("Processing compound search with operator, {} criteria", criteriaArray.size());
      for (JsonNode criteria : criteriaArray) {
        searchTerms.add(createSearchTerms(criteria));
      }
    } else {
      String field = jsonNode.get("field").asText();
      String value = jsonNode.get("value").asText();
      LOG.debug("Creating simple search term - field: {}", field);
      return switch (SearchCriteria.valueOf(field)) {
        case FROM -> new FromTerm(new InternetAddress(value));
        case SUBJECT -> new SubjectTerm(value);
        case BODY -> new BodyTerm(value);
      };
    }
    String operator = jsonNode.get("operator").asText();
    LOG.debug("Combining {} search terms with operator: {}", searchTerms.size(), operator);
    return switch (SearchOperator.valueOf(operator)) {
      case AND -> new AndTerm(searchTerms.toArray(new SearchTerm[0]));
      case OR -> new OrTerm(searchTerms.toArray(new SearchTerm[0]));
    };
  }

  private List<SearchEmailsResponse> searchEmails(Folder folder, Object criteria)
      throws MessagingException {
    LOG.debug("Executing email search on folder");
    folder.open(Folder.READ_ONLY);
    LOG.debug("Folder opened in READ_ONLY mode, converting criteria to search terms");
    JsonNode jsonNode = this.objectMapper.convertValue(criteria, JsonNode.class);
    SearchTerm searchTerm = createSearchTerms(jsonNode);
    LOG.debug("Executing search on {} messages", folder.getMessageCount());
    Message[] results = folder.search(searchTerm, folder.getMessages());
    LOG.debug("Search found {} matching message(s)", results.length);
    return Arrays.stream(results)
        .map(this.jakartaUtils::createBodylessEmail)
        .map(email -> new SearchEmailsResponse(email.messageId(), email.subject()))
        .toList();
  }

  private DeleteEmailResponse deleteEmail(Folder folder, String messageId)
      throws MessagingException {
    LOG.debug("Deleting email with messageId: {}", messageId);
    folder.open(Folder.READ_WRITE);
    LOG.debug("Folder opened in READ_WRITE mode, searching for message");
    Message[] messages = folder.search(new MessageIDTerm(messageId));
    LOG.debug("Search returned {} message(s)", messages.length);
    Message message =
        Arrays.stream(messages)
            .findFirst()
            .orElseThrow(
                () -> {
                  LOG.debug("Email with messageId {} not found for deletion", messageId);
                  return new MessagingException("No emails have been found with this ID");
                });
    LOG.debug("Marking message as deleted");
    this.jakartaUtils.markAsDeleted(message);
    LOG.debug("Email deleted successfully: {}", messageId);
    return new DeleteEmailResponse(messageId, true);
  }

  private Optional<InternetAddress[]> createParsedInternetAddresses(Object object)
      throws AddressException {
    if (Objects.isNull(object)) {
      LOG.debug("No addresses to parse (null input)");
      return Optional.empty();
    }
    LOG.debug("Parsing internet addresses from object type: {}", object.getClass().getSimpleName());
    return Optional.of(
        switch (object) {
          case List<?> list -> {
            LOG.debug("Parsing {} addresses from list", list.size());
            yield InternetAddress.parse(
                String.join(",", list.stream().map(Object::toString).toList()));
          }
          case String string -> {
            LOG.debug("Parsing addresses from string input");
            yield InternetAddress.parse(string);
          }
          default ->
              throw new IllegalStateException(
                  "Unexpected value: " + object + ". List or String was expected");
        });
  }

  private Consumer<Document> getDocumentConsumer(Multipart multipart) {
    return document -> {
      try {
        LOG.debug(
            "Adding attachment: {} (type: {})",
            document.metadata().getFileName(),
            document.metadata().getContentType());
        BodyPart attachment = new MimeBodyPart();
        DataSource dataSource =
            new ByteArrayDataSource(document.asInputStream(), document.metadata().getContentType());
        attachment.setDataHandler(new DataHandler(dataSource));
        attachment.setFileName(document.metadata().getFileName());
        multipart.addBodyPart(attachment);
        LOG.debug("Attachment added successfully: {}", document.metadata().getFileName());
      } catch (IOException | MessagingException e) {
        LOG.debug(
            "Failed to add attachment {}: {}", document.metadata().getFileName(), e.getMessage());
        throw new RuntimeException(e);
      }
    };
  }

  private List<Document> createDocumentList(
      List<EmailAttachment> attachments, OutboundConnectorContext connectorContext) {
    LOG.debug("Creating document list from {} attachment(s)", attachments.size());
    List<Document> documents =
        attachments.stream()
            .map(
                document -> {
                  LOG.debug(
                      "Creating document for attachment: {} (type: {})",
                      document.name(),
                      document.contentType());
                  return connectorContext.create(
                      DocumentCreationRequest.from(document.inputStream())
                          .fileName(document.name())
                          .contentType(document.contentType())
                          .build());
                })
            .toList();
    LOG.debug("Created {} document(s) from attachments", documents.size());
    return documents;
  }
}
