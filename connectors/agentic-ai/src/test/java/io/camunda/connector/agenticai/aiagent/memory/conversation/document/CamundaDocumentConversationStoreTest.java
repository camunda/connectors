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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext.DocumentContent;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.document.Document;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.document.store.DocumentCreationRequest;
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

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OutboundConnectorContext context;

  @Mock private CamundaDocumentStore documentStore;

  private CamundaDocumentConversationStore store;
  private RuntimeMemory memory;

  @Captor private ArgumentCaptor<DocumentCreationRequest> documentCreationRequestCaptor;

  @BeforeEach
  void setUp() {
    store =
        new CamundaDocumentConversationStore(
            new CamundaDocumentMemoryStorageConfiguration(
                Duration.ofHours(1), Map.of("customKey", "customValue")),
            documentStore,
            objectMapper,
            PREVIOUS_DOCUMENTS_RETENTION_SIZE);

    memory = new DefaultRuntimeMemory();
  }

  @Test
  void supportsAgentContextWithoutPreviousConversation() {
    final var agentContext = AgentContext.empty();

    store.loadIntoRuntimeMemory(context, agentContext, memory);

    assertThat(memory.allMessages()).isEmpty();
  }

  @Test
  void loadsPreviousConversationContext() throws Exception {
    final var document = mock(Document.class);
    when(document.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation").document(document).build();

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);

    store.loadIntoRuntimeMemory(context, agentContext, memory);

    assertThat(memory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
  }

  @Test
  void throwsExceptionForUnsupportedConversationContext() {
    final var agentContext =
        AgentContext.empty().withConversation(new TestConversationContext("dummy"));

    assertThatThrownBy(() -> store.loadIntoRuntimeMemory(context, agentContext, memory))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unsupported conversation context: TestConversationContext");
  }

  @Test
  void storesRuntimeMemoryIntoAgentContext_withEmptyPreviousConversation() throws Exception {
    mockJobContext();
    memory.addMessages(TEST_MESSAGES);

    final var document = mock(Document.class);
    when(context.create(documentCreationRequestCaptor.capture())).thenReturn(document);

    final var agentContext = AgentContext.empty();
    final var updatedAgentContext = store.storeFromRuntimeMemory(context, agentContext, memory);

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
  void storesRuntimeMemoryIntoAgentContext_withExistingPreviousConversation() throws Exception {
    mockJobContext();

    final var previousDocument = mock(Document.class);
    when(previousDocument.asInputStream())
        .thenReturn(documentContentAsInputStream(EXPECTED_DOCUMENT_CONTENT));

    final var previousConversationContext =
        CamundaDocumentConversationContext.builder("test-conversation")
            .document(previousDocument)
            .build();

    final var newDocument = mock(Document.class);
    when(context.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);
    store.loadIntoRuntimeMemory(context, agentContext, memory);

    final var userMessage = userMessage("User message");
    memory.addMessage(userMessage);

    final var updatedAgentContext = store.storeFromRuntimeMemory(context, agentContext, memory);

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
    when(context.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);
    store.loadIntoRuntimeMemory(context, agentContext, memory);

    final var updatedAgentContext = store.storeFromRuntimeMemory(context, agentContext, memory);
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
    when(context.create(documentCreationRequestCaptor.capture())).thenReturn(newDocument);

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);
    store.loadIntoRuntimeMemory(context, agentContext, memory);

    final var updatedAgentContext = store.storeFromRuntimeMemory(context, agentContext, memory);
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
    when(context.getJobContext().getBpmnProcessId()).thenReturn(BPMN_PROCESS_ID);
    when(context.getJobContext().getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(context.getJobContext().getElementId()).thenReturn(ELEMENT_ID);
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
