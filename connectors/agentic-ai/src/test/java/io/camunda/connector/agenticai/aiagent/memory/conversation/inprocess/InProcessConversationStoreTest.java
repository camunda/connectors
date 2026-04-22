/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.model.message.Message;
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
        new io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration
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
