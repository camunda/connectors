/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessageWithMixedToolKinds;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InProcessConversationStoreTest {

  private static final List<Message> TEST_MESSAGES = TestMessagesFixture.testMessages();

  @Mock private AgentExecutionContext executionContext;

  private final InProcessConversationStore store = new InProcessConversationStore();

  @Test
  void storeTypeIsAlignedWithConfiguration() {
    final var configuration =
        new io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration
            .InProcessMemoryStorageConfiguration();
    assertThat(store.type()).isEqualTo(configuration.storeType()).isEqualTo("in-process");
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
  void loadsPreviousConversationContext() {
    final var previousConversationContext =
        InProcessConversationContext.builder("test-conversation").messages(TEST_MESSAGES).build();

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
  void storesMessagesIntoConversationContext_withEmptyPreviousConversation() {
    final var agentContext = AgentContext.empty();

    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);
      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(TEST_MESSAGES));
      var updatedAgentContext = agentContext.withConversation(updatedConversation);

      assertThat(updatedAgentContext.conversation())
          .asInstanceOf(InstanceOfAssertFactories.type(InProcessConversationContext.class))
          .satisfies(
              conversation -> {
                assertThat(conversation.conversationId()).isNotEmpty();
                assertThat(conversation.messages()).containsExactlyElementsOf(TEST_MESSAGES);
              });
    }
  }

  @Test
  void keepsTheKindOfToolCallsWhenTheAgentContextIsSerializedAndLoadedAgain() throws Exception {
    final var objectMapper = TestObjectMapperSupplier.INSTANCE;
    final var assistantMessage = assistantMessageWithMixedToolKinds();

    final var agentContext = AgentContext.empty();
    AgentContext storedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);
      var storedConversation =
          session.storeMessages(
              agentContext,
              ConversationStoreRequest.of(List.of(userMessage("hi"), assistantMessage)));
      storedAgentContext = agentContext.withConversation(storedConversation);
    }

    // the agent context is persisted as a process variable
    final var reloadedAgentContext =
        objectMapper.readValue(
            objectMapper.writeValueAsString(storedAgentContext), AgentContext.class);

    try (var session = store.createSession(executionContext, reloadedAgentContext)) {
      final var loadedMessages = session.loadMessages(reloadedAgentContext).messages();

      assertThat(loadedMessages.getLast())
          .asInstanceOf(InstanceOfAssertFactories.type(AssistantMessage.class))
          .satisfies(
              loaded -> {
                assertThat(loaded.toolCalls())
                    .containsExactlyElementsOf(assistantMessage.toolCalls());
                assertThat(loaded.toolCalls())
                    .extracting(ToolCall::kind)
                    .containsExactly(ToolKind.RUNTIME, ToolKind.PROCESS);
              });
    }
  }

  @Test
  void keepsDocumentReferencesAndTheRegistryWhenTheAgentContextIsSerializedAndLoadedAgain()
      throws Exception {
    final var objectMapper = TestObjectMapperSupplier.INSTANCE;
    final var document = createDocument("report", "report.txt");
    final var entry =
        DocumentRegistries.entryFor(
            new IngestedDocument(document, DocumentSource.toolCall("fetch", "call-1")));
    final var reference = DocumentReferences.from(entry).withDelivery(DocumentDelivery.CONTENT);
    final var message =
        UserMessage.builder().content(List.of(TextContent.textContent("see"), reference)).build();

    final var agentContext =
        AgentContext.empty().withDocumentRegistry(DocumentRegistry.of(List.of(entry)));
    AgentContext storedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);
      var storedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(List.of(message)));
      storedAgentContext = agentContext.withConversation(storedConversation);
    }

    // the agent context is persisted as a process variable
    final var reloadedAgentContext =
        objectMapper.readValue(
            objectMapper.writeValueAsString(storedAgentContext), AgentContext.class);

    assertThat(reloadedAgentContext.documents()).containsExactly(entry);
    try (var session = store.createSession(executionContext, reloadedAgentContext)) {
      assertThat(session.loadMessages(reloadedAgentContext).messages())
          .singleElement()
          .isEqualTo(message);
    }
  }

  @Test
  void storesMessagesIntoConversationContext_withExistingPreviousConversation() {
    final var previousConversationContext =
        InProcessConversationContext.builder("test-conversation").messages(TEST_MESSAGES).build();

    final var userMessage = userMessage("User message");

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      final var allMessages = new ArrayList<>(loadResult.messages());
      allMessages.add(userMessage);

      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(allMessages));
      var updatedAgentContext = agentContext.withConversation(updatedConversation);

      assertThat(updatedAgentContext.conversation())
          .asInstanceOf(InstanceOfAssertFactories.type(InProcessConversationContext.class))
          .satisfies(
              conversation -> {
                final var expectedMessages = new ArrayList<>(TEST_MESSAGES);
                expectedMessages.add(userMessage);

                assertThat(conversation.conversationId())
                    .isEqualTo(previousConversationContext.conversationId());
                assertThat(conversation.messages()).containsExactlyElementsOf(expectedMessages);
              });
    }
  }
}
