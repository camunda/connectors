/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InProcessConversationStoreTest {

  private static final List<Message> TEST_MESSAGES = TestMessagesFixture.testMessages();

  @Mock private OutboundConnectorContext context;

  private final InProcessConversationStore store = new InProcessConversationStore();

  private RuntimeMemory memory;

  @BeforeEach
  void setUp() {
    memory = new DefaultRuntimeMemory();
  }

  @Test
  void supportsAgentContextWithoutPreviousConversation() {
    final var agentContext = AgentContext.empty();

    store.loadIntoRuntimeMemory(context, agentContext, memory);

    assertThat(memory.allMessages()).isEmpty();
  }

  @Test
  void loadsPreviousConversationContext() {
    final var previousConversationContext =
        InProcessConversationContext.builder("test-conversation").messages(TEST_MESSAGES).build();

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
  void storesRuntimeMemoryIntoAgentContext_withEmptyPreviousConversation() {
    memory.addMessages(TEST_MESSAGES);

    final var agentContext = AgentContext.empty();
    final var updatedAgentContext = store.storeFromRuntimeMemory(context, agentContext, memory);

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(InProcessConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.id()).isNotEmpty();
              assertThat(conversation.messages()).containsExactlyElementsOf(TEST_MESSAGES);
            });
  }

  @Test
  void storesRuntimeMemoryIntoAgentContext_withExistingPreviousConversation() {
    final var previousConversationContext =
        InProcessConversationContext.builder("test-conversation").messages(TEST_MESSAGES).build();

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);
    store.loadIntoRuntimeMemory(context, agentContext, memory);

    final var userMessage = UserMessage.userMessage("User message");
    memory.addMessage(userMessage);

    final var updatedAgentContext = store.storeFromRuntimeMemory(context, agentContext, memory);

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(InProcessConversationContext.class))
        .satisfies(
            conversation -> {
              final var expectedMessages = new ArrayList<>(TEST_MESSAGES);
              expectedMessages.add(userMessage);

              assertThat(conversation.id()).isEqualTo(previousConversationContext.id());
              assertThat(conversation.messages()).containsExactlyElementsOf(expectedMessages);
            });
  }
}
