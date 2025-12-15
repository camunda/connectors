/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.util;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import java.util.List;
import java.util.function.Consumer;

public interface MailClient {
  String getFolderId(Folder folder);

  /**
   * This method polls the Mailserver once and returns all matching messages. It then returns a
   * delta which needs to be passed to the funtion to continue from a given offset. This reduces the
   * likelyhood of reprocessing messages
   *
   * @param filterString an OData filter string that restricts the Messages returned
   * @param handler
   * @return The delta token
   */
  String getMessages(
      String deltaToken, Folder folder, String filterString, Consumer<EmailMessage> handler);

  void deleteMessage(EmailMessage msg, boolean force);

  void markMessageRead(EmailMessage msg);

  void moveMessage(EmailMessage msg, Folder folder);

  List<Document> fetchAttachments(EmailMessage msg);
}
