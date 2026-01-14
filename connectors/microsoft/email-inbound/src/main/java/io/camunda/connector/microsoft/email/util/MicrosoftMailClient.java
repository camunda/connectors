/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.util;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.core.tasks.PageIterator;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.messages.item.MessageItemRequestBuilder;
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.config.InboundAuthentication;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class MicrosoftMailClient implements MailClient {

  private final GraphServiceClient client;
  private final UserItemRequestBuilder graphClient;
  private final String userId;

  public MicrosoftMailClient(InboundAuthentication authentication, String userId) {
    // The client credentials flow requires that you request the
    // /.default scope, and pre-configure your permissions on the
    // app registration in Azure. An administrator must grant consent
    // to those permissions beforehand.
    final String[] scopes = new String[] {"https://graph.microsoft.com/.default"};

    final ClientSecretCredential credential =
        new ClientSecretCredentialBuilder()
            .clientId(authentication.clientId())
            .tenantId(authentication.tenantId())
            .clientSecret(authentication.clientSecret())
            .build();
    client = new GraphServiceClient(credential, scopes);
    this.userId = userId;
    graphClient = client.users().byUserId(userId);
  }

  @Override
  public String getFolderId(Folder folder) {
    return switch (folder) {
      case Folder.FolderById byId -> byId.folderId();
      case Folder.FolderByName byName -> getFolderIdByFolderName(byName.folderName());
    };
  }

  private String getFolderIdByFolderName(String folderName) {
    var resp =
        graphClient
            .mailFolders()
            .get(c -> c.queryParameters.filter = "displayName eq '" + folderName + "'");
    if (resp == null || resp.getValue() == null || resp.getValue().isEmpty()) {
      throw new ConnectorException(
          "No folder with name " + folderName + " could be found in mailbox " + userId);
    }
    if (resp.getValue().size() > 1) {
      throw new ConnectorException(
          "Multiple folders with name "
              + folderName
              + " exist in mailbox "
              + userId
              + ". Use folder ID instead.");
    }
    return resp.getValue().getFirst().getId();
  }

  class PageIteratorMessageFetcher implements OpaqueMessageFetcher {
    private final PageIterator<Message, MessageCollectionResponse> iterator;

    PageIteratorMessageFetcher(PageIterator<Message, MessageCollectionResponse> iterator) {
      this.iterator = iterator;
    }

    @Override
    public void poll() {
      try {
        iterator.iterate();
      } catch (ReflectiveOperationException e) {
        throw new ConnectorException(e);
      }
    }
  }

  @Override
  public OpaqueMessageFetcher constructMessageFetcher(
      Folder folder, String filterString, Consumer<EmailMessage> handler) {
    MessageCollectionResponse messageResponse =
        graphClient
            .mailFolders()
            .byMailFolderId(getFolderId(folder))
            .messages()
            .get(
                requestConfiguration -> {
                  requestConfiguration.headers.add("Prefer", "outlook.body-content-type=\"text\"");
                  requestConfiguration.queryParameters.filter = filterString;
                  requestConfiguration.queryParameters.select = EmailMessage.getSelect();
                  requestConfiguration.queryParameters.top = 10;
                });
    final var pageIterator =
        new PageIterator.Builder<Message, MessageCollectionResponse>()
            .client(client)
            .collectionPage(Objects.requireNonNull(messageResponse))
            .collectionPageFactory(MessageCollectionResponse::createFromDiscriminatorValue)
            .requestConfigurator(
                requestInfo -> {
                  // Re-add the header and query parameters to subsequent requests
                  requestInfo.headers.add("Prefer", "outlook.body-content-type=\"text\"");
                  requestInfo.addQueryParameter("%24select", filterString);
                  requestInfo.addQueryParameter("%24top", 10);
                  return requestInfo;
                })
            .processPageItemCallback(
                msg -> {
                  var myMsg = new EmailMessage(msg);
                  handler.accept(myMsg);
                  return true;
                });
    try {
      var iterator = pageIterator.build();
      return new PageIteratorMessageFetcher(iterator);
    } catch (ReflectiveOperationException e) {
      throw new ConnectorException(e);
    }
  }

  private MessageItemRequestBuilder constructCommonMessage(EmailMessage msg) {
    return graphClient.messages().byMessageId(msg.id());
  }

  @Override
  public void deleteMessage(EmailMessage msg, boolean force) {
    if (force) {
      constructCommonMessage(msg).permanentDelete();
    } else {
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
  public List<Document> fetchAttachments(InboundConnectorContext context, EmailMessage msg) {
    if (constructCommonMessage(msg).attachments().count().get() == 0) {
      return List.of();
    }
    var docs = new ArrayList<Document>();
    for (var attachment :
        Objects.requireNonNull(constructCommonMessage(msg).attachments().get().getValue())) {
      if (attachment instanceof FileAttachment file) {
        docs.add(
            context.create(
                DocumentCreationRequest.from(file.getContentBytes())
                    .fileName(file.getName())
                    .contentType(file.getContentType())
                    .build()));
      }
      // Note: ItemAttachment and ReferenceAttachment are intentionally not supported
    }
    return docs;
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
