/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.util;

import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import java.util.List;

public interface MailClient {
  String getFolderId(Folder folder);

  String getFolderIdByFolderName(String folderName);

  List<EmailMessage> getMessages(String filterString);

  void deleteMessage(EmailMessage msg, boolean force);

  void markMessageRead(EmailMessage msg);

  void moveMessage(EmailMessage msg, String targetFolderId);
}
