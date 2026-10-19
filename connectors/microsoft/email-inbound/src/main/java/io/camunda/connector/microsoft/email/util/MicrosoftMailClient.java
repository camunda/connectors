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
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.AttachmentCollectionResponse;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.messages.item.MessageItemRequestBuilder;
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody;
import com.microsoft.kiota.ApiException;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.microsoft.common.auth.BearerAuthentication;
import io.camunda.connector.microsoft.common.auth.ClientCredentialsAuthentication;
import io.camunda.connector.microsoft.common.auth.GraphServiceClientSupplier;
import io.camunda.connector.microsoft.common.auth.MicrosoftAuthentication;
import io.camunda.connector.microsoft.common.auth.RefreshTokenAuthentication;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.output.EmailAttachmentMetadata;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import io.camunda.connector.microsoft.email.model.output.GraphApiMapper;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicrosoftMailClient implements MailClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftMailClient.class);

  private final Supplier<GraphServiceClient> clientSupplier;
  private final String userId;
  private final InboundConnectorContext context;

  public MicrosoftMailClient(
      MicrosoftAuthentication authentication, String userId, InboundConnectorContext context) {
    this(createClientSupplier(authentication), userId, context);
  }

  MicrosoftMailClient(
      Supplier<GraphServiceClient> clientSupplier, String userId, InboundConnectorContext context) {
    this.userId = userId;
    this.context = context;
    this.clientSupplier = clientSupplier;
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
        // Rebuild on each call: DelegateAuthenticationProvider hands the SDK an AccessToken that
        // never reports as expired, so the SDK never refreshes it on its own - a fresh client is
        // the only way to pick up a new access token for the stored refresh token.
        var supplier = new GraphServiceClientSupplier();
        yield () -> supplier.buildAndGetGraphServiceClient(refreshToken);
      }
      case BearerAuthentication bearer -> {
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
        throw new ConnectorException(null, "Failed to construct message page iterator", e);
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
    // A message only ever reaches here with a resolved attachmentMetadata (processMessageItem
    // skips the message otherwise), so an empty list here is confirmed empty, not unknown.
    if (msg.attachmentMetadata().isEmpty()) {
      return List.of();
    }
    return fetchAttachmentPage(
            msg.id(),
            null,
            file ->
                context.create(
                    DocumentCreationRequest.from(file.getContentBytes())
                        .fileName(file.getName())
                        .contentType(file.getContentType())
                        .build()))
        .orElseThrow(
            () -> new ConnectorException("Failed to resolve attachments for email " + msg.id()));
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

  private boolean processMessageItem(Message msg, Consumer<EmailMessage> handler) {
    // Not gated on hasAttachments: Graph excludes inline attachments from that flag, so gating
    // on it would hide inline-only messages from isInline-based conditions.
    Optional<List<EmailAttachmentMetadata>> lookup;
    try {
      lookup =
          fetchAttachmentPage(
              msg.getId(),
              new String[] {"id", "name", "contentType", "size", "isInline"},
              MicrosoftMailClient::toMetadata);
    } catch (TransientAttachmentLookupException e) {
      // Rethrown, not returned false, so it aborts this poll instead of hammering an
      // already-throttled endpoint for every remaining message in the page.
      logAttachmentMetadataFailure(
          msg.getId(), "transient Graph error, will retry on next poll", e);
      throw e;
    }
    if (lookup.isEmpty()) {
      // Never hand the handler fabricated "no attachments" metadata; skip this message and
      // leave it for the next poll instead.
      logAttachmentMetadataFailure(
          msg.getId(), "skipping this email, will retry on next poll", null);
      return true;
    }
    handler.accept(GraphApiMapper.toEmailMessage(msg, lookup.get()));
    return true;
  }

  private void logAttachmentMetadataFailure(String messageId, String reason, Throwable cause) {
    LOGGER.warn(
        "Could not resolve attachment metadata for message {}: {}", messageId, reason, cause);
    context.log(
        activity ->
            activity
                .withSeverity(Severity.WARNING)
                .withTag("attachment-metadata-lookup")
                .withMessage(
                    "Could not resolve attachment metadata for email "
                        + messageId
                        + ": "
                        + reason));
  }

  static boolean isTransient(ApiException e) {
    int status = e.getResponseStatusCode();
    // 0 means the exception was constructed without an HTTP response at all (e.g. wrapping a
    // lower-level failure) - treat unknown status the same as throttled/unavailable, since we'd
    // rather retry a permanent failure than mistake a transient one for "no attachments".
    return status == 0 || status == 429 || status >= 500;
  }

  private static EmailAttachmentMetadata toMetadata(FileAttachment attachment) {
    return new EmailAttachmentMetadata(
        attachment.getId(),
        attachment.getName(),
        attachment.getContentType(),
        attachment.getSize() == null ? null : attachment.getSize().longValue(),
        attachment.getIsInline());
  }

  // Shared util so metadata and full download don't drift
  private <T> Optional<List<T>> fetchAttachmentPage(
      String messageId, String[] select, Function<FileAttachment, T> mapper) {
    try {
      var client = getClient();
      var response =
          client
              .users()
              .byUserId(userId)
              .messages()
              .byMessageId(messageId)
              .attachments()
              .get(
                  requestConfiguration -> {
                    if (select != null) {
                      requestConfiguration.queryParameters.select = select;
                    }
                  });
      if (response == null || response.getValue() == null) {
        return Optional.empty();
      }
      var items = new ArrayList<T>();
      PageIterator<Attachment, AttachmentCollectionResponse> iterator =
          new PageIterator.Builder<Attachment, AttachmentCollectionResponse>()
              .client(client)
              .collectionPage(response)
              .collectionPageFactory(AttachmentCollectionResponse::createFromDiscriminatorValue)
              .requestConfigurator(
                  // Graph's @odata.nextLink for subsequent pages already carries the original
                  // $select; interpageIterate() routes the next-page URL through the request's
                  // raw URL template rather than these query parameters, so nothing needs to be
                  // (and nothing here would successfully be) re-added for later pages.
                  requestInfo -> requestInfo)
              .processPageItemCallback(
                  attachment -> {
                    if (attachment instanceof FileAttachment file) {
                      items.add(mapper.apply(file));
                    }
                    return true;
                  })
              .build();
      iterator.iterate();
      return Optional.of(items);
    } catch (ApiException e) {
      if (isTransient(e)) {
        throw new TransientAttachmentLookupException(messageId, e);
      }
      // A permanent failure must not abort polling or trigger an endless retry loop against this
      // message; callers skip the message instead of treating this the same as "confirmed empty".
      LOGGER.warn("Failed to resolve attachments for message {}", messageId, e);
      return Optional.empty();
    } catch (ReflectiveOperationException e) {
      throw new ConnectorException(
          null, "Failed to construct attachment page iterator for message " + messageId, e);
    } catch (RuntimeException e) {
      if (ExceptionUtils.indexOfType(e, IOException.class) >= 0) {
        // The Kiota OkHttp adapter wraps connection-level failures (timeouts, connection resets,
        // DNS) in a bare RuntimeException rather than an ApiException, so they'd otherwise fall
        // through to the generic catch below and be mistaken for "no attachments". Walking the
        // full cause chain (not just the direct cause) since some paths add an extra wrapper.
        throw new TransientAttachmentLookupException(messageId, e);
      }
      LOGGER.warn("Failed to resolve attachments for message {}", messageId, e);
      return Optional.empty();
    }
  }

  /** Signals a transient (throttled/unavailable) Graph failure, as opposed to a permanent one. */
  static final class TransientAttachmentLookupException extends RuntimeException {
    TransientAttachmentLookupException(String messageId, Throwable cause) {
      super("Transient Graph failure resolving attachments for message " + messageId, cause);
    }
  }
}
