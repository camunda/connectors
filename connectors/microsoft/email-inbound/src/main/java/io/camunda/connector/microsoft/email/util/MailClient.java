/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.util;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import java.util.List;
import java.util.function.Consumer;

public interface MailClient {
  interface OpaqueMessageFetcher {
    void poll();
  }

  /**
   * Construct a client that can be repeatedly polled to process messages using the handler It
   * preserves its iteration position internally. As such, there should be no concurrent access to
   * it
   *
   * @param filterString an OData filter string that restricts the Messages returned
   * @param handler a function to which each received message will be passed
   * @return the consuming client
   */
  OpaqueMessageFetcher constructMessageFetcher(
      Folder folder, String filterString, Consumer<EmailMessage> handler);

  void deleteMessage(EmailMessage msg, boolean force);

  void markMessageRead(EmailMessage msg);

  void moveMessage(EmailMessage msg, Folder folder);

  List<Document> fetchAttachments(InboundConnectorContext context, EmailMessage msg);
}
