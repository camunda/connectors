/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.microsoft.graph.models.AttachmentCollectionResponse;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.output.EmailAddress;
import io.camunda.connector.microsoft.email.model.output.EmailAttachmentMetadata;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MicrosoftMailClientTest {

  private static final String USER_ID = "user@example.com";
  private static final String MESSAGE_ID = "msg-1";

  private final GraphServiceClient graphServiceClient =
      mock(GraphServiceClient.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
  private final MicrosoftMailClient client =
      new MicrosoftMailClient(
          () -> graphServiceClient, USER_ID, mock(InboundConnectorContext.class));

  private static EmailMessage messageWithAttachmentMetadata(
      List<EmailAttachmentMetadata> attachmentMetadata) {
    return new EmailMessage(
        MESSAGE_ID,
        "conv-1",
        new EmailAddress("Sender", "sender@example.com"),
        List.of(),
        List.of(),
        List.of(),
        "Subject",
        "Body",
        "text",
        OffsetDateTime.parse("2025-01-15T10:30:00Z"),
        null,
        attachmentMetadata,
        List.of());
  }

  @Test
  void fetchAttachments_metadataEmpty_skipsGraphCallAndReturnsEmpty() {
    var result =
        client.fetchAttachments(
            mock(InboundConnectorContext.class), messageWithAttachmentMetadata(List.of()));

    assertThat(result).isEmpty();
    verifyNoInteractions(graphServiceClient);
  }

  @Test
  void fetchAttachments_metadataNonEmpty_downloadsAttachments() {
    var fileAttachment = new FileAttachment();
    fileAttachment.setName("invoice.pdf");
    fileAttachment.setContentType("application/pdf");
    fileAttachment.setContentBytes(new byte[] {1, 2, 3});
    var response = new AttachmentCollectionResponse();
    response.setValue(List.of(fileAttachment));

    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .messages()
            .byMessageId(MESSAGE_ID)
            .attachments()
            .get(any()))
        .thenReturn(response);
    var downloaded = mock(Document.class);
    var context = mock(InboundConnectorContext.class);
    when(context.create(any())).thenReturn(downloaded);

    var pdfMetadata =
        new EmailAttachmentMetadata("att-1", "invoice.pdf", "application/pdf", 3L, false);
    var result =
        client.fetchAttachments(context, messageWithAttachmentMetadata(List.of(pdfMetadata)));

    assertThat(result).containsExactly(downloaded);
  }

  @Test
  void fetchAttachments_downloadTransientFailure_propagates() {
    var pdfMetadata =
        new EmailAttachmentMetadata("att-1", "invoice.pdf", "application/pdf", 3L, false);
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .messages()
            .byMessageId(MESSAGE_ID)
            .attachments()
            .get(any()))
        .thenThrow(apiExceptionWithStatus(429));

    assertThatThrownBy(
            () ->
                client.fetchAttachments(
                    mock(InboundConnectorContext.class),
                    messageWithAttachmentMetadata(List.of(pdfMetadata))))
        .isInstanceOf(MicrosoftMailClient.TransientAttachmentLookupException.class)
        .hasCauseInstanceOf(ApiException.class);
  }

  @Test
  void fetchAttachments_downloadPermanentFailure_throwsInsteadOfReturningEmpty() {
    // A permanent failure at this stage must not silently degrade to "no attachments": unlike
    // the metadata path, this runs after the activation condition already matched.
    var pdfMetadata =
        new EmailAttachmentMetadata("att-1", "invoice.pdf", "application/pdf", 3L, false);
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .messages()
            .byMessageId(MESSAGE_ID)
            .attachments()
            .get(any()))
        .thenThrow(apiExceptionWithStatus(404));

    assertThatThrownBy(
            () ->
                client.fetchAttachments(
                    mock(InboundConnectorContext.class),
                    messageWithAttachmentMetadata(List.of(pdfMetadata))))
        .isInstanceOf(ConnectorException.class);
  }

  private static Message graphMessage(String id, Boolean hasAttachments) {
    var message = new Message();
    message.setId(id);
    message.setHasAttachments(hasAttachments);
    return message;
  }

  private static MessageCollectionResponse oneMessagePage(Message message) {
    var response = new MessageCollectionResponse();
    response.setValue(List.of(message));
    return response;
  }

  @Test
  void poll_metadataResolves_handlerReceivesMappedMetadata() {
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .mailFolders()
            .byMailFolderId("inbox")
            .messages()
            .get(any()))
        .thenReturn(oneMessagePage(graphMessage(MESSAGE_ID, false)));
    var pdf = new FileAttachment();
    pdf.setId("att-1");
    pdf.setName("invoice.pdf");
    pdf.setContentType("application/pdf");
    pdf.setIsInline(false);
    var attachmentsResponse = new AttachmentCollectionResponse();
    attachmentsResponse.setValue(List.of(pdf));
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .messages()
            .byMessageId(MESSAGE_ID)
            .attachments()
            .get(any()))
        .thenReturn(attachmentsResponse);

    var received = new ArrayList<EmailMessage>();
    client.constructMessageFetcher(new Folder.FolderById("inbox"), null).poll(received::add);

    assertThat(received).hasSize(1);
    assertThat(received.get(0).attachmentMetadata())
        .extracting(EmailAttachmentMetadata::name)
        .containsExactly("invoice.pdf");
  }

  @Test
  void poll_permanentMetadataFailure_handlerNotInvokedAndPollContinues() {
    // A permanent failure (e.g. 403/404) must skip this message rather than hand it to the
    // handler with fabricated "no attachments" metadata - see
    // fetchAttachments_metadataEmpty_skipsGraphCallAndReturnsEmpty for why that matters: an
    // activation condition could wrongly not match and the email get postprocessed away.
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .mailFolders()
            .byMailFolderId("inbox")
            .messages()
            .get(any()))
        .thenReturn(oneMessagePage(graphMessage(MESSAGE_ID, true)));
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .messages()
            .byMessageId(MESSAGE_ID)
            .attachments()
            .get(any()))
        .thenThrow(apiExceptionWithStatus(404));

    var received = new ArrayList<EmailMessage>();
    client.constructMessageFetcher(new Folder.FolderById("inbox"), null).poll(received::add);

    assertThat(received).isEmpty();
  }

  @Test
  void poll_transientMetadataFailure_handlerNotInvokedAndPollStops() {
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .mailFolders()
            .byMailFolderId("inbox")
            .messages()
            .get(any()))
        .thenReturn(oneMessagePage(graphMessage(MESSAGE_ID, true)));
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .messages()
            .byMessageId(MESSAGE_ID)
            .attachments()
            .get(any()))
        .thenThrow(apiExceptionWithStatus(429));

    // Propagated out of poll() (rather than swallowed) so EmailPollingWorker.run() reports the
    // connector unhealthy for this poll cycle instead of silently reporting Health.up().
    var invocations = new AtomicInteger();
    assertThatThrownBy(
            () ->
                client
                    .constructMessageFetcher(new Folder.FolderById("inbox"), null)
                    .poll(msg -> invocations.incrementAndGet()))
        .isInstanceOf(MicrosoftMailClient.TransientAttachmentLookupException.class)
        .hasCauseInstanceOf(ApiException.class);

    assertThat(invocations.get()).isZero();
  }

  @Test
  void poll_ioExceptionWrappedInRuntimeException_isTreatedAsTransient() {
    // The Kiota OkHttp adapter wraps connection-level failures (timeouts, resets, DNS) in a bare
    // RuntimeException rather than an ApiException; this must still be classified transient, not
    // mistaken for a permanent "no attachments" degrade.
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .mailFolders()
            .byMailFolderId("inbox")
            .messages()
            .get(any()))
        .thenReturn(oneMessagePage(graphMessage(MESSAGE_ID, true)));
    when(graphServiceClient
            .users()
            .byUserId(USER_ID)
            .messages()
            .byMessageId(MESSAGE_ID)
            .attachments()
            .get(any()))
        .thenThrow(new RuntimeException(new IOException("connection reset")));

    var invocations = new AtomicInteger();
    assertThatThrownBy(
            () ->
                client
                    .constructMessageFetcher(new Folder.FolderById("inbox"), null)
                    .poll(msg -> invocations.incrementAndGet()))
        .isInstanceOf(MicrosoftMailClient.TransientAttachmentLookupException.class);

    assertThat(invocations.get()).isZero();
  }

  @Test
  void isTransient_throttledOrServerError_isTransient() {
    assertThat(MicrosoftMailClient.isTransient(apiExceptionWithStatus(429))).isTrue();
    assertThat(MicrosoftMailClient.isTransient(apiExceptionWithStatus(500))).isTrue();
    assertThat(MicrosoftMailClient.isTransient(apiExceptionWithStatus(503))).isTrue();
  }

  @Test
  void isTransient_noResponseStatus_isTransient() {
    // Status 0 means the exception was built without an HTTP response at all - treated as
    // transient since mistaking a genuinely transient failure for permanent risks data loss.
    assertThat(MicrosoftMailClient.isTransient(apiExceptionWithStatus(0))).isTrue();
  }

  @Test
  void isTransient_clientError_isPermanent() {
    assertThat(MicrosoftMailClient.isTransient(apiExceptionWithStatus(400))).isFalse();
    assertThat(MicrosoftMailClient.isTransient(apiExceptionWithStatus(404))).isFalse();
  }

  private static ApiException apiExceptionWithStatus(int status) {
    return new TestApiException(status);
  }

  // ApiException.setResponseStatusCode is protected with no public equivalent; a same-file
  // subclass can still reach it via inheritance to build a fixture with a given status.
  private static final class TestApiException extends ApiException {
    TestApiException(int status) {
      setResponseStatusCode(status);
    }
  }
}
