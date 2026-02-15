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
import io.camunda.connector.microsoft.email.model.output.GraphApiMapper;
import java.lang.reflect.InvocationTargetException;
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
    graphClient = client.users().byUserId(userId);
    this.userId = userId;
  }

  private String getFolderId(Folder folder) {
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
    private final Folder folder;
    private final String filterString;
    private final Consumer<EmailMessage> handler;

    PageIteratorMessageFetcher(Folder folder, String filterString, Consumer<EmailMessage> handler) {
      this.folder = folder;
      this.filterString = filterString;
      this.handler = handler;
    }

    @Override
    public void poll() {
      try {
        MessageCollectionResponse messageResponse = fetchMessages(folder, filterString);
        PageIterator<Message, MessageCollectionResponse> iterator =
            getPageIterator(filterString, handler, messageResponse);
        iterator.iterate();
      } catch (ReflectiveOperationException e) {
        throw new ConnectorException(e);
      }
    }
  }

  @Override
  public OpaqueMessageFetcher constructMessageFetcher(
      Folder folder, String filterString, Consumer<EmailMessage> handler) {
    return new PageIteratorMessageFetcher(folder, filterString, handler);
  }

  private PageIterator<Message, MessageCollectionResponse> getPageIterator(
      String filterString,
      Consumer<EmailMessage> handler,
      MessageCollectionResponse messageResponse)
      throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    return new PageIterator.Builder<Message, MessageCollectionResponse>()
        .client(client)
        .collectionPage(Objects.requireNonNull(messageResponse))
        .collectionPageFactory(MessageCollectionResponse::createFromDiscriminatorValue)
        .requestConfigurator(
            requestInfo -> {
              // Re-add the header and query parameters to subsequent requests
              requestInfo.headers.add("Prefer", "outlook.body-content-type=\"text\"");
              if (filterString != null && !filterString.isEmpty()) {
                requestInfo.addQueryParameter("%24filter", filterString);
              }
              requestInfo.addQueryParameter(
                  "%24select", String.join(",", EmailMessage.getSelect()));
              requestInfo.addQueryParameter("%24top", 10);
              return requestInfo;
            })
        .processPageItemCallback(msg -> processMessageItem(msg, handler))
        .build();
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

  private MessageCollectionResponse fetchMessages(Folder folder, String filterString) {
    return graphClient
        .mailFolders()
        .byMailFolderId(getFolderId(folder))
        .messages()
        .get(
            requestConfiguration -> {
              requestConfiguration.headers.add("Prefer", "outlook.body-content-type=\"text\"");
              if (filterString != null && !filterString.isEmpty()) {
                requestConfiguration.queryParameters.filter = filterString;
              }
              requestConfiguration.queryParameters.select = EmailMessage.getSelect();
              requestConfiguration.queryParameters.top = 10;
            });
  }

  private static boolean processMessageItem(Message msg, Consumer<EmailMessage> handler) {
    var myMsg = GraphApiMapper.toEmailMessage(msg, List.of());
    handler.accept(myMsg);
    return true;
  }
}
