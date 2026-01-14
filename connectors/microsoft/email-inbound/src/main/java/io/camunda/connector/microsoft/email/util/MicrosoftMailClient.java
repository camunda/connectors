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
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.mailfolders.item.messages.delta.DeltaGetResponse;
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

  public MicrosoftMailClient(InboundAuthentication auth, String userId) {
    // The client credentials flow requires that you request the
    // /.default scope, and pre-configure your permissions on the
    // app registration in Azure. An administrator must grant consent
    // to those permissions beforehand.
    final String[] scopes = new String[] {"https://graph.microsoft.com/.default"};

    final ClientSecretCredential credential =
        new ClientSecretCredentialBuilder()
            .clientId(auth.clientId())
            .tenantId(auth.tenantId())
            .clientSecret(auth.clientSecret())
            .build();
    client = new GraphServiceClient(credential, scopes);
    this.userId = userId;
    graphClient = client.users().byUserId(userId);
  }

  private String getFolderId(Folder folder) {
    if (folder.isFolderId()) {
      return folder.folderName();
    }
    // TODO: This technically allows people to sneak in arbitrary filters
    // But is this an injection scenario we care about?
    var resp =
        graphClient
            .mailFolders()
            .get(c -> c.queryParameters.filter = "displayName eq '" + folder.folderName() + "'");
    if (resp.getValue().size() > 1) {
      throw new ConnectorException(
          "Folder name "
              + folder.folderName()
              + " matches more than one folder in mailbox "
              + userId);
    }
    if (resp.getValue().isEmpty()) {
      throw new ConnectorException(
          "No folder with name " + folder.folderName() + " could be found in mailbox " + userId);
    }
    return resp.getValue().getFirst().getId();
  }

  record PageIteratorMessageFetcher(PageIterator<Message, DeltaGetResponse> iterator)
      implements OpaqueMessageFetcher {
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
    DeltaGetResponse messageResponse =
        graphClient
            .mailFolders()
            .byMailFolderId(getFolderId(folder))
            .messages()
            .delta()
            .get(
                requestConfiguration -> {
                  requestConfiguration.headers.add("Prefer", "outlook.body-content-type=\"text\"");
                  requestConfiguration.queryParameters.filter = filterString;
                  requestConfiguration.queryParameters.select = EmailMessage.getSelect();
                  requestConfiguration.queryParameters.top = 10;
                });
    final var pageIterator =
        new PageIterator.Builder<Message, DeltaGetResponse>()
            .client(client)
            .collectionPage(Objects.requireNonNull(messageResponse))
            .collectionPageFactory(DeltaGetResponse::createFromDiscriminatorValue)
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
    ArrayList<Document> docs = new ArrayList<>();
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
}
