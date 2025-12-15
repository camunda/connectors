/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.email.util;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.messages.item.MessageItemRequestBuilder;
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.azure.email.model.config.Folder;
import io.camunda.connector.azure.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.azure.email.model.output.EmailMessage;
import java.util.List;
import java.util.function.Consumer;

public class MicrosoftMailClient implements MailClient {

  private final GraphServiceClient client;
  private final UserItemRequestBuilder graphClient;

  public MicrosoftMailClient(MsInboundEmailProperties properties) {
    // The client credentials flow requires that you request the
    // /.default scope, and pre-configure your permissions on the
    // app registration in Azure. An administrator must grant consent
    // to those permissions beforehand.
    final String[] scopes = new String[] {"https://graph.microsoft.com/.default"};

    final ClientSecretCredential credential =
        new ClientSecretCredentialBuilder()
            .clientId(properties.authentication().clientId())
            .tenantId(properties.authentication().tenantId())
            .clientSecret(properties.authentication().clientSecret())
            .build();
    client = new GraphServiceClient(credential, scopes);
    graphClient = client.users().byUserId(properties.pollingConfig().userId());
  }

  @Override
  public String getFolderId(Folder folder) {
    if (folder.isFolderId()) {
      return folder.folderName();
    }
    // TODO: This technically allows people to sneak in arbitrary filters
    // But is this an injection scenario we care about?
    var resp =
        graphClient
            .mailFolders()
            .get(c -> c.queryParameters.filter = "displayName eq '" + folder.folderName() + "'");
    // TODO: Should we specify the name of the user mailbox in the error message?
    if (resp.getValue().size() > 1) {
      throw new ConnectorException(
          "Folder name " + folder.folderName() + " matches more than one folder.");
    }
    if (resp.getValue().isEmpty()) {
      throw new ConnectorException(
          "No folder with name " + folder.folderName() + " could be found.");
    }
    return resp.getValue().getFirst().getId();
  }

  @Override
  public String getMessages(
      String deltaToken, String filterString, Consumer<EmailMessage> handler) {
    return null;
  }

  private MessageItemRequestBuilder constructCommonMessage(EmailMessage msg) {
    return graphClient.messages().byMessageId(msg.id());
  }

  @Override
  public void deleteMessage(EmailMessage msg, boolean force) {
    if (force) constructCommonMessage(msg).permanentDelete();
    else {
      constructCommonMessage(msg).delete();
    }
  }

  @Override
  public void markMessageRead(EmailMessage msg) {
    Message updatedMessage = new Message();
    updatedMessage.setIsRead(true);
    constructCommonMessage(msg).patch(updatedMessage);
  }

  @Override
  public void moveMessage(EmailMessage msg, Folder folder) {
    String folderId = getFolderId(folder);
    var body = new MovePostRequestBody();
    body.setDestinationId(folderId);
    constructCommonMessage(msg).move().post(body);
  }

  @Override
  public List<Document> fetchAttachments(EmailMessage msg) {
    return List.of();
  }

  @Deprecated
  public UserItemRequestBuilder getClient() {
    return this.graphClient;
  }

  @Deprecated
  public GraphServiceClient getGraphclient() {
    return this.client;
  }
}
