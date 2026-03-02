/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.util;

import static io.camunda.connector.microsoft.email.MsEmailInboundConstants.ODATA_FILTER_PARAM;
import static io.camunda.connector.microsoft.email.MsEmailInboundConstants.ODATA_SELECT_PARAM;
import static io.camunda.connector.microsoft.email.MsEmailInboundConstants.ODATA_TOP_PARAM;
import static io.camunda.connector.microsoft.email.MsEmailInboundConstants.PAGE_SIZE;
import static io.camunda.connector.microsoft.email.MsEmailInboundConstants.PREFER_HEADER;
import static io.camunda.connector.microsoft.email.MsEmailInboundConstants.PREFER_TEXT_BODY;

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
import io.camunda.connector.microsoft.common.auth.BearerAuthentication;
import io.camunda.connector.microsoft.common.auth.ClientCredentialsAuthentication;
import io.camunda.connector.microsoft.common.auth.GraphServiceClientSupplier;
import io.camunda.connector.microsoft.common.auth.MicrosoftAuthentication;
import io.camunda.connector.microsoft.common.auth.RefreshTokenAuthentication;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import io.camunda.connector.microsoft.email.model.output.GraphApiMapper;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

public class MicrosoftMailClient implements MailClient {

  private final Supplier<GraphServiceClient> clientSupplier;
  private final String userId;

  public MicrosoftMailClient(MicrosoftAuthentication authentication, String userId) {
    this.userId = userId;
    this.clientSupplier = createClientSupplier(authentication);
  }

  private static Supplier<GraphServiceClient> createClientSupplier(
      MicrosoftAuthentication authentication) {
    return switch (authentication) {
      case ClientCredentialsAuthentication clientCreds -> {
        // Client credentials flow: Azure SDK handles token renewal internally via
        // ClientSecretCredential, so we create the client once and reuse it.
        var supplier = new GraphServiceClientSupplier();
        final GraphServiceClient client = supplier.buildAndGetGraphServiceClient(clientCreds);
        yield () -> client;
      }
      case RefreshTokenAuthentication refreshToken -> {
        // Refresh token flow: rebuild the client on each poll cycle to get a fresh access token,
        // since refresh tokens may be rotated by the identity provider.
        var supplier = new GraphServiceClientSupplier();
        yield () -> supplier.buildAndGetGraphServiceClient(refreshToken);
      }
      case BearerAuthentication bearer -> {
        // Bearer token: create the client once with the provided token.
        // Note: bearer tokens are short-lived and not recommended for polling connectors.
        var supplier = new GraphServiceClientSupplier();
        final GraphServiceClient client = supplier.buildAndGetGraphServiceClient(bearer);
        yield () -> client;
      }
    };
  }

  private GraphServiceClient getClient() {
    return clientSupplier.get();
  }

  private UserItemRequestBuilder getGraphClient() {
    return getClient().users().byUserId(userId);
  }

  private String getFolderId(Folder folder) {
    return switch (folder) {
      case Folder.FolderById byId -> byId.folderId();
      case Folder.FolderByName byName -> getFolderIdByFolderName(byName.folderName());
    };
  }

  private String getFolderIdByFolderName(String folderName) {
    // Escape single quotes per OData standard: single quotes in string literals must be doubled
    // to prevent injection attacks (e.g., "O'Reilly" becomes "O''Reilly")
    var resp =
        getGraphClient()
            .mailFolders()
            .get(
                c ->
                    c.queryParameters.filter =
                        String.format("displayName eq '%s'", folderName.replace("'", "''")));
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

    PageIteratorMessageFetcher(Folder folder, String filterString) {
      this.folder = folder;
      this.filterString = filterString;
    }

    @Override
    public void poll(Consumer<EmailMessage> handler) {
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
  public OpaqueMessageFetcher constructMessageFetcher(Folder folder, String filterString) {
    return new PageIteratorMessageFetcher(folder, filterString);
  }

  private PageIterator<Message, MessageCollectionResponse> getPageIterator(
      String filterString,
      Consumer<EmailMessage> handler,
      MessageCollectionResponse messageResponse)
      throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    return new PageIterator.Builder<Message, MessageCollectionResponse>()
        .client(getClient())
        .collectionPage(Objects.requireNonNull(messageResponse))
        .collectionPageFactory(MessageCollectionResponse::createFromDiscriminatorValue)
        .requestConfigurator(
            requestInfo -> {
              // Re-add the header and query parameters to subsequent requests
              requestInfo.headers.add(PREFER_HEADER, PREFER_TEXT_BODY);
              if (StringUtils.isNotBlank(filterString)) {
                requestInfo.addQueryParameter(ODATA_FILTER_PARAM, filterString);
              }
              requestInfo.addQueryParameter(
                  ODATA_SELECT_PARAM, String.join(",", EmailMessage.getSelect()));
              requestInfo.addQueryParameter(ODATA_TOP_PARAM, PAGE_SIZE);
              return requestInfo;
            })
        .processPageItemCallback(msg -> processMessageItem(msg, handler))
        .build();
  }

  private MessageItemRequestBuilder constructCommonMessage(EmailMessage msg) {
    return getGraphClient().messages().byMessageId(msg.id());
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
    var messageBuilder = constructCommonMessage(msg);
    if (messageBuilder.attachments().count().get() == 0) {
      return List.of();
    }
    var docs = new ArrayList<Document>();
    for (var attachment : Objects.requireNonNull(messageBuilder.attachments().get().getValue())) {
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
    return getGraphClient()
        .mailFolders()
        .byMailFolderId(getFolderId(folder))
        .messages()
        .get(
            requestConfiguration -> {
              requestConfiguration.headers.add(PREFER_HEADER, PREFER_TEXT_BODY);
              if (StringUtils.isNotBlank(filterString)) {
                requestConfiguration.queryParameters.filter = filterString;
              }
              requestConfiguration.queryParameters.select = EmailMessage.getSelect();
              requestConfiguration.queryParameters.top = PAGE_SIZE;
            });
  }

  private static boolean processMessageItem(Message msg, Consumer<EmailMessage> handler) {
    var myMsg = GraphApiMapper.toEmailMessage(msg, List.of());
    handler.accept(myMsg);
    return true;
  }
}
