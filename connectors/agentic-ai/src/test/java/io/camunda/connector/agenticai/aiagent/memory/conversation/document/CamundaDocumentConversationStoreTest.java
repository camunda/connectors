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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext.DocumentContent;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    final var session = store.createSession(executionContext, agentContext);
    final var loadResult = session.loadMessages(agentContext);

    assertThat(loadResult.messages()).isEmpty();
    assertThat(loadResult.reconciledFromStore()).isFalse();
  }

  @Test
  void loadsPreviousConversationContext() throws Exception {
    final var document = mock(Document.class);
    when(document.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation").document(document).build();

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);

    final var session = store.createSession(executionContext, agentContext);
    final var loadResult = session.loadMessages(agentContext);

    assertThat(loadResult.messages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(loadResult.reconciledFromStore()).isFalse();
  }

  @Test
  void throwsExceptionForUnsupportedConversationContext() {
    final var agentContext =
        AgentContext.empty().withConversation(new TestConversationContext("dummy"));

    final var session = store.createSession(executionContext, agentContext);

    assertThatThrownBy(() -> session.loadMessages(agentContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unsupported conversation context: TestConversationContext");
  }

  @Test
  void storesMessagesIntoAgentContext_withEmptyPreviousConversation() throws Exception {
    mockJobContext();

    final var document = mock(Document.class);
    when(documentFactory.create(documentCreationRequestCaptor.capture())).thenReturn(document);

    final var agentContext = AgentContext.empty();
    final var session = store.createSession(executionContext, agentContext);
    session.loadMessages(agentContext);
    final var updatedAgentContext = session.storeMessages(agentContext, TEST_MESSAGES);

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(CamundaDocumentConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.conversationId()).isNotEmpty();
              assertThat(conversation.version()).isEqualTo(1);
              assertThat(conversation.document()).isEqualTo(document);
            });

    final var creationRequest = documentCreationRequestCaptor.getValue();
    assertDocumentCreationRequest(creationRequest, updatedAgentContext);
    JSONAssert.assertEquals(
        new String(creationRequest.content().readAllBytes()),
        documentContentAsString(EXPECTED_DOCUMENT_CONTENT),
        true);
  }

  @Test
  void storesMessagesIntoAgentContext_withExistingPreviousConversation() throws Exception {
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
    final var session = store.createSession(executionContext, agentContext);
    final var loadResult = session.loadMessages(agentContext);

    final var allMessages = new ArrayList<>(loadResult.messages());
    allMessages.add(userMessage);

    final var updatedAgentContext = session.storeMessages(agentContext, allMessages);

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(CamundaDocumentConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.conversationId()).isNotEmpty();
              assertThat(conversation.version()).isEqualTo(1);
              assertThat(conversation.document()).isEqualTo(newDocument);
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

  @Test
  void incrementsVersionOnSubsequentStores() throws Exception {
    mockJobContext();

    final var previousDocument = mock(Document.class);
    when(previousDocument.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation")
            .version(5)
            .document(previousDocument)
            .build();

    final var newDocument = mock(Document.class);
    when(documentFactory.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);
    final var session = store.createSession(executionContext, agentContext);
    session.loadMessages(agentContext);
    final var updatedAgentContext = session.storeMessages(agentContext, TEST_MESSAGES);

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(CamundaDocumentConversationContext.class))
        .satisfies(conversation -> assertThat(conversation.version()).isEqualTo(6));
  }

  @Test
  void onJobCompletedDeletesPreviousDocument() throws Exception {
    mockJobContext();

    final var previousDocumentReference = mock(CamundaDocumentReference.class);
    final var previousDocument = mock(Document.class);
    when(previousDocument.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));
    when(previousDocument.reference()).thenReturn(previousDocumentReference);

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation")
            .document(previousDocument)
            .build();

    final var newDocument = mock(Document.class);
    when(documentFactory.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);
    final var session = store.createSession(executionContext, agentContext);
    session.loadMessages(agentContext);
    final var updatedAgentContext = session.storeMessages(agentContext, TEST_MESSAGES);

    session.onJobCompleted(updatedAgentContext);

    verify(documentStore).deleteDocument(previousDocumentReference);
  }

  @Test
  void migratesFromInProcessConversationContext() throws Exception {
    mockJobContext();

    final var previousConversationContext =
        InProcessConversationContext.builder("migrated-conversation")
            .version(3)
            .messages(TEST_MESSAGES)
            .build();

    final var newDocument = mock(Document.class);
    when(documentFactory.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);
    final var session = store.createSession(executionContext, agentContext);
    final var loadResult = session.loadMessages(agentContext);

    assertThat(loadResult.messages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(loadResult.reconciledFromStore()).isFalse();

    final var updatedAgentContext = session.storeMessages(agentContext, TEST_MESSAGES);

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(CamundaDocumentConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.conversationId()).isEqualTo("migrated-conversation");
              assertThat(conversation.version()).isEqualTo(4);
              assertThat(conversation.document()).isEqualTo(newDocument);
            });

    final var creationRequest = documentCreationRequestCaptor.getValue();
    JSONAssert.assertEquals(
        new String(creationRequest.content().readAllBytes()),
        documentContentAsString(EXPECTED_DOCUMENT_CONTENT),
        true);
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
    when(executionContext.jobContext().bpmnProcessId()).thenReturn(BPMN_PROCESS_ID);
    when(executionContext.jobContext().processInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(executionContext.jobContext().elementId()).thenReturn(ELEMENT_ID);
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
