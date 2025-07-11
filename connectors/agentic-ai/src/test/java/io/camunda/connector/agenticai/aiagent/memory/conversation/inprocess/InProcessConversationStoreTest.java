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
import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentJobContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.Message;
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

  @Mock private AgentJobContext agentJobContext;
  @Mock private AgentRequest agentRequest;
  private AgentExecutionContext executionContext;

  private final InProcessConversationStore store = new InProcessConversationStore();

  private RuntimeMemory memory;

  @BeforeEach
  void setUp() {
    executionContext = new AgentExecutionContext(agentJobContext, agentRequest);
    memory = new DefaultRuntimeMemory();
  }

  @Test
  void storeTypeIsAlignedWithConfiguration() {
    final var configuration = new InProcessMemoryStorageConfiguration();
    assertThat(store.type()).isEqualTo(configuration.storeType()).isEqualTo("in-process");
  }

  @Test
  void supportsAgentContextWithoutPreviousConversation() {
    final var agentContext = AgentContext.empty();

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    assertThat(memory.allMessages()).isEmpty();
  }

  @Test
  void loadsPreviousConversationContext() {
    final var previousConversationContext =
        InProcessConversationContext.builder("test-conversation").messages(TEST_MESSAGES).build();

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    assertThat(memory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
  }

  @Test
  void throwsExceptionForUnsupportedConversationContext() {
    final var agentContext =
        AgentContext.empty().withConversation(new TestConversationContext("dummy"));

    assertThatThrownBy(
            () ->
                store.executeInSession(
                    executionContext,
                    agentContext,
                    session -> {
                      session.loadIntoRuntimeMemory(agentContext, memory);
                      return agentResponse(agentContext);
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unsupported conversation context: TestConversationContext");
  }

  @Test
  void storesRuntimeMemoryIntoAgentContext_withEmptyPreviousConversation() {
    memory.addMessages(TEST_MESSAGES);

    final var agentContext = AgentContext.empty();
    final var updatedAgentContext =
        store
            .executeInSession(
                executionContext,
                agentContext,
                session -> agentResponse(session.storeFromRuntimeMemory(agentContext, memory)))
            .context();

    assertThat(updatedAgentContext.conversation())
        .asInstanceOf(InstanceOfAssertFactories.type(InProcessConversationContext.class))
        .satisfies(
            conversation -> {
              assertThat(conversation.conversationId()).isNotEmpty();
              assertThat(conversation.messages()).containsExactlyElementsOf(TEST_MESSAGES);
            });
  }

  @Test
  void storesRuntimeMemoryIntoAgentContext_withExistingPreviousConversation() {
    final var previousConversationContext =
        InProcessConversationContext.builder("test-conversation").messages(TEST_MESSAGES).build();

    final var userMessage = userMessage("User message");

    final var agentContext = AgentContext.empty().withConversation(previousConversationContext);
    final var updatedAgentContext =
        store
            .executeInSession(
                executionContext,
                agentContext,
                session -> {
                  session.loadIntoRuntimeMemory(agentContext, memory);

                  memory.addMessage(userMessage);

                  return agentResponse(session.storeFromRuntimeMemory(agentContext, memory));
                })
            .context();

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

  private AgentResponse agentResponse(AgentContext agentContext) {
    return AgentResponse.builder().context(agentContext).build();
  }
}
