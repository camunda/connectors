/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.microsoft.email.model.config.EmailProcessingOperation;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.output.EmailAddress;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageProcessorTest {

  @Mock private InboundConnectorContext context;

  private TestMailClient spyMailClient;

  private static final String TEST_MESSAGE_ID = "test-msg-id-123";
  private static final String TEST_FOLDER_ID = "inbox-folder-id";
  private static final String TARGET_FOLDER_ID = "archive-folder-id";
  private static final OffsetDateTime TEST_RECEIVED_TIME =
      OffsetDateTime.parse("2025-01-15T10:30:00Z");

  @BeforeEach
  void setUp() {
    TestMailClient testMailClient =
        new TestMailClient(
            Map.of(
                TEST_FOLDER_ID, new ArrayList<>(),
                TARGET_FOLDER_ID, new ArrayList<>()),
            Map.of("inbox", TEST_FOLDER_ID, "archive", TARGET_FOLDER_ID));
    spyMailClient = spy(testMailClient);
  }

  private EmailMessage createTestMessage() {
    return createTestMessage(List.of());
  }

  private EmailMessage createTestMessage(List<Document> attachments) {
    return new EmailMessage(
        TEST_MESSAGE_ID,
        "conversation-123",
        new EmailAddress("Sender Name", "sender@example.com"),
        List.of(new EmailAddress("Recipient Name", "recipient@example.com")),
        List.of(), // cc
        List.of(), // bcc
        "Test Subject",
        "Test Body",
        TEST_RECEIVED_TIME,
        attachments);
  }

  private void setupSuccessfulCorrelation() {
    when(context.canActivate(any()))
        .thenReturn(
            new ActivationCheckResult.Success.CanActivate(
                new ProcessElementWithRuntimeData("test", 0, 0, "test", "<default>")));
    when(context.correlate(any(CorrelationRequest.class)))
        .thenReturn(
            new CorrelationResult.Success.ProcessInstanceCreated(
                new ProcessElementWithRuntimeData("test", 0, 0, "test", "<default>"), 0L, "test"));
    doReturn(List.of()).when(spyMailClient).fetchAttachments(any(), any());
  }

  @Nested
  class PostprocessingOperations {

    @Test
    void handleMessage_MarkAsReadOperation_callsMarkMessageRead() {
      // Given
      var operation = new EmailProcessingOperation.MarkAsReadOperation();
      var message = createTestMessage();
      setupSuccessfulCorrelation();
      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then
      verify(spyMailClient, times(1)).markMessageRead(message);
      verify(spyMailClient, never()).deleteMessage(any(), anyBoolean());
      verify(spyMailClient, never()).moveMessage(any(), any());
    }

    @Test
    void handleMessage_DeleteOperation_callsDeleteMessage() {
      // Given
      var operation = new EmailProcessingOperation.DeleteOperation(false);
      var message = createTestMessage();
      setupSuccessfulCorrelation();
      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then
      verify(spyMailClient, times(1)).deleteMessage(message, false);
      verify(spyMailClient, never()).markMessageRead(any());
      verify(spyMailClient, never()).moveMessage(any(), any());
    }

    @Test
    void handleMessage_DeleteOperationForce_callsDeleteMessageWithForceTrue() {
      // Given
      var operation = new EmailProcessingOperation.DeleteOperation(true);
      var message = createTestMessage();
      setupSuccessfulCorrelation();
      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then
      verify(spyMailClient, times(1)).deleteMessage(message, true);
      verify(spyMailClient, never()).markMessageRead(any());
      verify(spyMailClient, never()).moveMessage(any(), any());
    }

    @Test
    void handleMessage_MoveOperation_callsMoveMessage() {
      // Given
      var targetFolder = new Folder.FolderById(TARGET_FOLDER_ID);
      var operation = new EmailProcessingOperation.MoveOperation(targetFolder);
      var message = createTestMessage();
      setupSuccessfulCorrelation();
      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then
      verify(spyMailClient, times(1)).moveMessage(message, targetFolder);
      verify(spyMailClient, never()).deleteMessage(any(), anyBoolean());
      verify(spyMailClient, never()).markMessageRead(any());
    }
  }

  @Nested
  class ActivationCheckScenarios {

    @Test
    void handleMessage_NoMatchingElement_discardUnmatchedTrue_doesNotPostprocess() {
      // Given
      var operation = new EmailProcessingOperation.MarkAsReadOperation();
      var message = createTestMessage();

      when(context.canActivate(any()))
          .thenReturn(new ActivationCheckResult.Failure.NoMatchingElement(true));

      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then - no postprocessing should occur
      verify(spyMailClient, never()).markMessageRead(any());
      verify(spyMailClient, never()).deleteMessage(any(), anyBoolean());
      verify(spyMailClient, never()).moveMessage(any(), any());
    }

    @Test
    void handleMessage_NoMatchingElement_discardUnmatchedFalse_doesPostprocess() {
      // Given
      var operation = new EmailProcessingOperation.MarkAsReadOperation();
      var message = createTestMessage();

      when(context.canActivate(any()))
          .thenReturn(new ActivationCheckResult.Failure.NoMatchingElement(false));

      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then - postprocessing should occur
      verify(spyMailClient, times(1)).markMessageRead(message);
    }

    @Test
    void handleMessage_TooManyMatchingElements_doesNotPostprocess() {
      // Given
      var operation = new EmailProcessingOperation.MarkAsReadOperation();
      var message = createTestMessage();

      when(context.canActivate(any()))
          .thenReturn(new ActivationCheckResult.Failure.TooManyMatchingElements());

      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then - no postprocessing should occur
      verify(spyMailClient, never()).markMessageRead(any());
      verify(spyMailClient, never()).deleteMessage(any(), anyBoolean());
      verify(spyMailClient, never()).moveMessage(any(), any());
    }
  }

  @Nested
  class CorrelationResultScenarios {

    private void setupCanActivateSuccess() {
      when(context.canActivate(any()))
          .thenReturn(
              new ActivationCheckResult.Success.CanActivate(
                  new ProcessElementWithRuntimeData("test", 0, 0, "test", "<default>")));
      doReturn(List.of()).when(spyMailClient).fetchAttachments(any(), any());
    }

    @Test
    void handleMessage_CorrelationSuccess_postprocesses() {
      // Given
      var operation = new EmailProcessingOperation.DeleteOperation(false);
      var message = createTestMessage();
      setupSuccessfulCorrelation();
      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then
      verify(spyMailClient, times(1)).fetchAttachments(context, message);
      verify(spyMailClient, times(1)).deleteMessage(message, false);
    }

    @Test
    void handleMessage_CorrelationFailure_ForwardErrorToUpstream_doesNotPostprocess() {
      // Given
      var operation = new EmailProcessingOperation.MarkAsReadOperation();
      var message = createTestMessage();

      setupCanActivateSuccess();
      when(context.correlate(any(CorrelationRequest.class)))
          .thenReturn(
              new CorrelationResult.Failure.InvalidInput(
                  "error", new RuntimeException("test error")));

      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then - ForwardErrorToUpstream strategy means no postprocessing
      verify(spyMailClient, times(1)).fetchAttachments(context, message);
      verify(spyMailClient, never()).markMessageRead(any());
      verify(spyMailClient, never()).deleteMessage(any(), anyBoolean());
      verify(spyMailClient, never()).moveMessage(any(), any());
    }

    @Test
    void handleMessage_CorrelationFailure_Ignore_doesPostprocess() {
      // Given
      var operation = new EmailProcessingOperation.MarkAsReadOperation();
      var message = createTestMessage();

      setupCanActivateSuccess();
      when(context.correlate(any(CorrelationRequest.class)))
          .thenReturn(new CorrelationResult.Failure.ActivationConditionNotMet(true));

      var processor = new MessageProcessor(operation, spyMailClient, context);

      // When
      processor.handleMessage(message);

      // Then - Ignore strategy means postprocessing happens
      verify(spyMailClient, times(1)).fetchAttachments(context, message);
      verify(spyMailClient, times(1)).markMessageRead(message);
    }
  }
}
