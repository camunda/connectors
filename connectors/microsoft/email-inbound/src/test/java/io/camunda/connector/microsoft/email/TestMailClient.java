/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import io.camunda.connector.microsoft.email.util.MailClient;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestMailClient implements MailClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(TestMailClient.class);
  private final Map<String, String> nameToIdMapping;
  private final Map<String, List<EmailMessage>> mailboxState;

  public TestMailClient(
      Map<String, List<EmailMessage>> mailboxState, Map<String, String> nameToIdMapping) {
    this.mailboxState = mailboxState;
    this.nameToIdMapping = nameToIdMapping;
  }

  record TestMessageFetcher(Folder folder, Consumer<EmailMessage> handler)
      implements OpaqueMessageFetcher {

    @Override
    public void poll() {}
  }

  @Override
  public String getFolderId(Folder folder) {
    return switch (folder) {
      case Folder.FolderById byId -> byId.folderId();
      case Folder.FolderByName byName -> getFolderIdByFolderName(byName.folderName());
    };
  }

  private String getFolderIdByFolderName(String folderName) {
    return nameToIdMapping.get(folderName);
  }

  @Override
  public OpaqueMessageFetcher constructMessageFetcher(
      Folder folder, String filterString, Consumer<EmailMessage> handler) {
    return new TestMessageFetcher(folder, handler);
  }

  @Override
  public void deleteMessage(EmailMessage msg, boolean force) {
    mailboxState.values().forEach(l -> l.remove(msg));
  }

  @Override
  public void markMessageRead(EmailMessage msg) {
    LOGGER.info("Marked msg {} as read", msg);
  }

  @Override
  public void moveMessage(EmailMessage msg, Folder folder) {
    mailboxState
        .values()
        .forEach(
            l -> {
              l.remove(msg);
            });
    mailboxState.get(getFolderId(folder)).add(msg);
  }

  @Override
  public List<Document> fetchAttachments(InboundConnectorContext context, EmailMessage msg) {
    // You have to set up the message with attachments before passing them to the test client
    return msg.attachments();
  }
}
