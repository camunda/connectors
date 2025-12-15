/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.email.util;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.azure.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.azure.email.model.output.EmailMessage;
import java.util.List;

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
  public String getFolderIdByFolderName(String folderName) {
    // TODO: This technically allows people to sneak in arbitrary filters
    // But is this an injection scenario we care about?
    var resp =
        graphClient
            .mailFolders()
            .get(c -> c.queryParameters.filter = "displayName eq " + folderName);
    // TODO: Should we specify the name of the user mailbox in the error message?
    if (resp.getValue().size() > 1) {
      throw new ConnectorException("Folder name " + folderName + " matches more than one folder.");
    }
    if (resp.getValue().isEmpty()) {
      throw new ConnectorException("No folder with name " + folderName + " could be found.");
    }
    return resp.getValue().getFirst().getId();
  }

  @Override
  public List<EmailMessage> getMessages(String filterString) {
    return List.of();
  }

  @Override
  public void deleteMessage(EmailMessage msg, boolean force) {}

  @Override
  public void markMessageRead(EmailMessage msg) {}

  @Override
  public void moveMessage(EmailMessage msg, String targetFolderId) {}

  @Deprecated
  public UserItemRequestBuilder getClient() {
    return this.graphClient;
  }

  @Deprecated
  public GraphServiceClient getGraphclient() {
    return this.client;
  }
}
