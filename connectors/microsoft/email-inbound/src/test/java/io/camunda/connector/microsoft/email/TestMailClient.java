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
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestMailClient implements MailClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(TestMailClient.class);
  private final Map<String, String> nameToIdMapping;
  private final Map<String, List<InternalMessage>> mailboxState;

  public record InternalMessage(EmailMessage msg, boolean isRead) {}

  public TestMailClient(
      Map<String, List<InternalMessage>> mailboxState, Map<String, String> nameToIdMapping) {
    this.mailboxState = mailboxState;
    this.nameToIdMapping = nameToIdMapping;
  }

  public class TestMessageFetcher implements OpaqueMessageFetcher {
    private final Folder folder;
    private final Consumer<EmailMessage> handler;
    private final String filter;

    private TestMessageFetcher(Folder folder, String filter, Consumer<EmailMessage> handler) {
      this.folder = folder;
      this.handler = handler;
      this.filter = filter;
    }

    @Override
    public void poll() {
      // TODO: this should run into ConcurrentModificationException
      // How do we persist iteration state while the list is potentially but guaranteed to be
      // modified
      //
      mailboxState.get(getFolderId(folder)).forEach(m -> handler.accept(m.msg()));
    }
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
    return new TestMessageFetcher(folder, filterString, handler);
  }

  @Override
  public void deleteMessage(EmailMessage msg, boolean force) {
    mailboxState.values().forEach(l -> l.removeIf(e -> e.msg().equals(msg)));
  }

  @Override
  public void markMessageRead(EmailMessage msg) {
    LOGGER.info("Marked msg {} as read", msg);
  }

  @Override
  public void moveMessage(EmailMessage msg, Folder folder) {
    Optional<Boolean> readState =
        mailboxState.values().stream()
            // Map reduce because java doesn't like mutating local variables from inside lambdas
            .map(
                l -> {
                  for (int index = 0; index < l.size(); index++) {
                    var e = l.get(index);
                    if (e.msg().equals(msg)) {
                      l.remove(index);
                      return Optional.of(e.isRead);
                    }
                  }
                  return Optional.<Boolean>empty();
                })
            .reduce(Optional.empty(), (l, r) -> l.isPresent() ? l : r);
    mailboxState.get(getFolderId(folder)).add(new InternalMessage(msg, readState.orElse(false)));
  }

  @Override
  public List<Document> fetchAttachments(InboundConnectorContext context, EmailMessage msg) {
    // You have to set up the message with attachments before passing them to the test client
    return msg.attachments();
  }
}
