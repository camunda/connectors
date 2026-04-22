/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
class CamundaDocumentConversationStoreTest {

  private static final List<Message> TEST_MESSAGES = TestMessagesFixture.testMessages();
  private static final DocumentContent EXPECTED_DOCUMENT_CONTENT =
      new DocumentContent(TEST_MESSAGES);

  private static final String BPMN_PROCESS_ID = "test-process-id";
  private static final Long PROCESS_INSTANCE_KEY = 123456L;
  private static final String ELEMENT_ID = "AI_Agent";

  private static final int PREVIOUS_DOCUMENTS_RETENTION_SIZE = 2;

  @Mock private DocumentFactory documentFactory;
  @Mock private CamundaDocumentStore documentStore;
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AgentExecutionContext executionContext;

  private CamundaDocumentConversationStore store;

  @Captor private ArgumentCaptor<DocumentCreationRequest> documentCreationRequestCaptor;

  @BeforeEach
  void setUp() {
    when(executionContext.memory())
        .thenReturn(
            new MemoryConfiguration(
                new CamundaDocumentMemoryStorageConfiguration(
                    Duration.ofHours(1), Map.of("customKey", "customValue")),
                20));

    store = new CamundaDocumentConversationStore(documentFactory, documentStore, objectMapper);
  }

  @Test
  void storeTypeIsAlignedWithConfiguration() {
    final var configuration =
        new CamundaDocumentMemoryStorageConfiguration(Duration.ofHours(1), Map.of());
    assertThat(store.type()).isEqualTo(configuration.storeType()).isEqualTo("camunda-document");
  }

  @Test
  void throwsExceptionForMissingConfiguration() {
    final var agentContext = AgentContext.empty();
    when(executionContext.memory()).thenReturn(new MemoryConfiguration(null, 20));

    assertThatThrownBy(() -> store.createSession(executionContext, agentContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected memory storage configuration to be of type CamundaDocumentMemoryStorageConfiguration, but got: null");
  }

  @Test
  void throwsExceptionForUnsupportedConfiguration() {
    final var agentContext = AgentContext.empty();
    when(executionContext.memory())
        .thenReturn(
            new MemoryConfiguration(
                new MemoryStorageConfiguration.InProcessMemoryStorageConfiguration(), 20));

    assertThatThrownBy(() -> store.createSession(executionContext, agentContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            "Expected memory storage configuration to be of type CamundaDocumentMemoryStorageConfiguration, but got:")
        .hasMessageContaining("InProcessMemoryStorageConfiguration");
  }

  @Test
  void supportsAgentContextWithoutPreviousConversation() {
    final var agentContext = AgentContext.empty();

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      assertThat(loadResult.messages()).isEmpty();
    }
  }

  @Test
  void loadsPreviousConversationContext() throws Exception {
    final var document = mock(Document.class);
    when(document.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation").document(document).build();

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      assertThat(loadResult.messages()).containsExactlyElementsOf(TEST_MESSAGES);
    }
  }

  @Test
  void throwsExceptionForUnsupportedConversationContext() {
    final var agentContext =
        AgentContext.empty().withConversation(new TestConversationContext("dummy"));

    try (var session = store.createSession(executionContext, agentContext)) {
      assertThatThrownBy(() -> session.loadMessages(agentContext))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Unsupported conversation context: TestConversationContext");
    }
  }

  @Test
  void storesMessagesIntoConversationContext_withEmptyPreviousConversation() throws Exception {
    mockJobContext();

    final var document = mock(Document.class);
    when(documentFactory.create(documentCreationRequestCaptor.capture())).thenReturn(document);

    final var agentContext = AgentContext.empty();

    AgentContext updatedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);
      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(TEST_MESSAGES));
      updatedAgentContext = agentContext.withConversation(updatedConversation);
    }

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(CamundaDocumentConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.conversationId()).isNotEmpty();
              assertThat(conversation.document()).isEqualTo(document);
              assertThat(conversation.previousDocuments()).isEmpty();
            });

    final var creationRequest = documentCreationRequestCaptor.getValue();
    assertDocumentCreationRequest(creationRequest, updatedAgentContext);
    JSONAssert.assertEquals(
        new String(creationRequest.content().readAllBytes()),
        documentContentAsString(EXPECTED_DOCUMENT_CONTENT),
        true);
  }

  @Test
  void storesMessagesIntoConversationContext_withExistingPreviousConversation() throws Exception {
    mockJobContext();

    final var previousDocument = mock(Document.class);
    when(previousDocument.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation")
            .document(previousDocument)
            .build();

    final var newDocument = mock(Document.class);
    when(documentFactory.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var userMessage = userMessage("User message");

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);

    AgentContext updatedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      final var allMessages = new ArrayList<>(loadResult.messages());
      allMessages.add(userMessage);

      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(allMessages));
      updatedAgentContext = agentContext.withConversation(updatedConversation);
    }

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(CamundaDocumentConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.conversationId()).isNotEmpty();
              assertThat(conversation.document()).isEqualTo(newDocument);
              assertThat(conversation.previousDocuments()).containsExactly(previousDocument);
            });

    final var expectedMessages = new ArrayList<>(TEST_MESSAGES);
    expectedMessages.add(userMessage);

    final var creationRequest = documentCreationRequestCaptor.getValue();
    assertDocumentCreationRequest(creationRequest, updatedAgentContext);
    JSONAssert.assertEquals(
        new String(creationRequest.content().readAllBytes()),
        documentContentAsString(new DocumentContent(expectedMessages)),
        true);
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 4})
  void purgesPreviousDocumentsGreaterThanRetentionSize(int previousDocumentsCount)
      throws Exception {
    final var previousDocuments =
        IntStream.range(0, previousDocumentsCount).mapToObj(i -> mock(Document.class)).toList();

    final var previousDocument = mock(Document.class);
    when(previousDocument.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation")
            .document(previousDocument)
            .previousDocuments(previousDocuments)
            .build();

    // expect that the store will purge previous documents up to retention size + add
    // the current one at the end of the list
    final var expectedDeletedDocuments = new ArrayList<CamundaDocumentReference>();
    final var expectedPreviousDocuments = new ArrayList<>(previousDocuments);
    while (expectedPreviousDocuments.size() > PREVIOUS_DOCUMENTS_RETENTION_SIZE - 1) {
      final var documentReference = mock(CamundaDocumentReference.class);
      final var mockedDocument = expectedPreviousDocuments.removeFirst();
      when(mockedDocument.reference()).thenReturn(documentReference);
      expectedDeletedDocuments.add(documentReference);
    }
    expectedPreviousDocuments.add(previousDocument);

    final var newDocument = mock(Document.class);
    when(documentFactory.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);

    AgentContext updatedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);
      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(loadResult.messages()));
      updatedAgentContext = agentContext.withConversation(updatedConversation);
    }

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(CamundaDocumentConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.conversationId()).isNotEmpty();
              assertThat(conversation.document()).isEqualTo(newDocument);
              assertThat(conversation.previousDocuments())
                  .containsExactlyElementsOf(expectedPreviousDocuments);
            });

    expectedDeletedDocuments.forEach(
        reference -> {
          verify(documentStore).deleteDocument(reference);
        });
  }

  @Test
  void doesNotFailWhenPurgingASinglePreviousDocumentFails() throws Exception {
    final var previousDocuments =
        IntStream.range(0, 3).mapToObj(i -> mock(Document.class)).toList();

    final var previousDocument = mock(Document.class);
    when(previousDocument.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation")
            .document(previousDocument)
            .previousDocuments(previousDocuments)
            .build();

    final var failingReference = mock(CamundaDocumentReference.class);
    when(previousDocuments.get(0).reference()).thenReturn(failingReference);
    when(previousDocuments.get(1).reference()).thenReturn(mock(CamundaDocumentReference.class));

    doThrow(new RuntimeException("Test exception"))
        .when(documentStore)
        .deleteDocument(failingReference);

    final var newDocument = mock(Document.class);
    when(documentFactory.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);

    AgentContext updatedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);
      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(loadResult.messages()));
      updatedAgentContext = agentContext.withConversation(updatedConversation);
    }

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(CamundaDocumentConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.conversationId()).isNotEmpty();
              assertThat(conversation.document()).isEqualTo(newDocument);
              assertThat(conversation.previousDocuments())
                  .containsExactly(
                      previousDocuments.get(0), // this failed to be purged
                      previousDocuments.get(2),
                      previousDocument);
            });
  }

  @Test
  void onJobCompletionFailed_deletesOrphanedDocument() {
    var documentReference = mock(CamundaDocumentReference.class);
    var document = mock(Document.class);
    when(document.reference()).thenReturn(documentReference);

    var conversationContext =
        CamundaDocumentConversationContext.builder("test-conversation").document(document).build();
    var agentContext = AgentContext.empty().withConversation(conversationContext);

    store.onJobCompletionFailed(
        executionContext,
        agentContext,
        new JobCompletionFailure.CommandFailed(new RuntimeException("test")));

    verify(documentStore).deleteDocument(documentReference);
  }

  @Test
  void onJobCompletionFailed_swallowsDeleteFailure() {
    var documentReference = mock(CamundaDocumentReference.class);
    var document = mock(Document.class);
    when(document.reference()).thenReturn(documentReference);
    doThrow(new RuntimeException("delete failed"))
        .when(documentStore)
        .deleteDocument(documentReference);

    var conversationContext =
        CamundaDocumentConversationContext.builder("test-conversation").document(document).build();
    var agentContext = AgentContext.empty().withConversation(conversationContext);

    // should not throw
    store.onJobCompletionFailed(
        executionContext,
        agentContext,
        new JobCompletionFailure.CommandFailed(new RuntimeException("test")));

    verify(documentStore).deleteDocument(documentReference);
  }

  @Test
  void onJobCompletionFailed_noOpWithoutConversationContext() {
    var agentContext = AgentContext.empty();

    store.onJobCompletionFailed(
        executionContext,
        agentContext,
        new JobCompletionFailure.CommandFailed(new RuntimeException("test")));

    verifyNoInteractions(documentStore);
  }

  @Test
  void onJobCompletionFailed_noOpForNonCamundaDocumentReference() {
    var document = mock(Document.class);
    when(document.reference()).thenReturn(mock(DocumentReference.class));

    var conversationContext =
        CamundaDocumentConversationContext.builder("test-conversation").document(document).build();
    var agentContext = AgentContext.empty().withConversation(conversationContext);

    store.onJobCompletionFailed(
        executionContext,
        agentContext,
        new JobCompletionFailure.CommandFailed(new RuntimeException("test")));

    verify(documentStore, never()).deleteDocument(any());
  }

  private void assertDocumentCreationRequest(
      DocumentCreationRequest creationRequest, AgentContext updatedAgentContext) {
    assertThat(creationRequest.processDefinitionId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(creationRequest.processInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
    assertThat(creationRequest.contentType()).isEqualTo("application/json");
    assertThat(creationRequest.fileName()).isEqualTo(ELEMENT_ID + "_conversation.json");
    assertThat(creationRequest.customProperties())
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("conversationId", updatedAgentContext.conversation().conversationId()),
                Map.entry("customKey", "customValue")));
  }

  private void mockJobContext() {
    when(executionContext.jobContext().getBpmnProcessId()).thenReturn(BPMN_PROCESS_ID);
    when(executionContext.jobContext().getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(executionContext.jobContext().getElementId()).thenReturn(ELEMENT_ID);
  }

  private InputStream documentContentAsInputStream(DocumentContent documentContent)
      throws JsonProcessingException {
    return new ByteArrayInputStream(
        documentContentAsString(documentContent).getBytes(StandardCharsets.UTF_8));
  }

  private String documentContentAsString(DocumentContent documentContent)
      throws JsonProcessingException {
    return objectMapper.writeValueAsString(documentContent);
  }
}
