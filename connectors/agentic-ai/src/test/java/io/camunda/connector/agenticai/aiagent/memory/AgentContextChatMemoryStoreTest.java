/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.serializedTestMessages;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.testMessages;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentContextChatMemoryStoreTest {

  private static final String DEFAULT_MEMORY_ID = "default";
  private static final String TEST_MEMORY_ID = "test";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private InMemoryChatMemoryStore delegateStore;
  private AgentContextChatMemoryStore agentContextMemoryStore;

  @BeforeEach
  void setUp() {
    delegateStore = new InMemoryChatMemoryStore();
    agentContextMemoryStore = new AgentContextChatMemoryStore(delegateStore, objectMapper);
  }

  @ParameterizedTest
  @ValueSource(strings = {DEFAULT_MEMORY_ID, TEST_MEMORY_ID})
  void retrievesMessagesFromDelegateStore(String memoryId) {
    assertThat(delegateStore.getMessages(memoryId)).isEmpty();
    assertThat(agentContextMemoryStore.getMessages(memoryId)).isEmpty();

    final var testMessages = testMessages();
    delegateStore.updateMessages(memoryId, testMessages);

    assertThat(delegateStore.getMessages(memoryId)).containsExactlyElementsOf(testMessages);
    assertThat(agentContextMemoryStore.getMessages(memoryId))
        .containsExactlyElementsOf(testMessages);
  }

  @ParameterizedTest
  @ValueSource(strings = {DEFAULT_MEMORY_ID, TEST_MEMORY_ID})
  void updatesMessagesInDelegateStore(String memoryId) {
    assertThat(delegateStore.getMessages(memoryId)).isEmpty();
    assertThat(agentContextMemoryStore.getMessages(memoryId)).isEmpty();

    final var testMessages = testMessages();
    agentContextMemoryStore.updateMessages(memoryId, testMessages);

    assertThat(delegateStore.getMessages(memoryId)).containsExactlyElementsOf(testMessages);
    assertThat(agentContextMemoryStore.getMessages(memoryId))
        .containsExactlyElementsOf(testMessages);
  }

  @ParameterizedTest
  @ValueSource(strings = {DEFAULT_MEMORY_ID, TEST_MEMORY_ID})
  void deletesMessagesInDelegateStore(String memoryId) {
    final var testMessages = testMessages();
    agentContextMemoryStore.updateMessages(memoryId, testMessages);

    assertThat(delegateStore.getMessages(memoryId)).containsExactlyElementsOf(testMessages);
    assertThat(agentContextMemoryStore.getMessages(memoryId))
        .containsExactlyElementsOf(testMessages);

    agentContextMemoryStore.deleteMessages(memoryId);

    assertThat(delegateStore.getMessages(memoryId)).isEmpty();
    assertThat(agentContextMemoryStore.getMessages(memoryId)).isEmpty();
  }

  @Test
  void storesMessagesToAgentContextWithDefaultMemoryId() throws Exception {
    agentContextMemoryStore.updateMessages(DEFAULT_MEMORY_ID, testMessages());

    AgentContext agentContext = AgentContext.empty();
    assertThat(agentContext.memory()).isEmpty();

    final var expectedMemory = serializedTestMessages();
    agentContext = agentContextMemoryStore.storeToAgentContext(agentContext);
    assertThat(agentContext.memory()).containsExactlyElementsOf(expectedMemory);
  }

  @Test
  void storesMessagesToAgentContextWithCustomMemoryId() throws Exception {
    agentContextMemoryStore.updateMessages(TEST_MEMORY_ID, testMessages());

    AgentContext agentContext = AgentContext.empty();
    assertThat(agentContext.memory()).isEmpty();

    final var expectedMemory = serializedTestMessages();
    agentContext = agentContextMemoryStore.storeToAgentContext(agentContext, TEST_MEMORY_ID);
    assertThat(agentContext.memory()).containsExactlyElementsOf(expectedMemory);
  }

  @Test
  void loadsMessagesFromAgentContextWithDefaultMemoryId() throws Exception {
    assertThat(agentContextMemoryStore.getMessages(DEFAULT_MEMORY_ID)).isEmpty();

    AgentContext agentContext = AgentContext.empty().withMemory(serializedTestMessages());

    agentContextMemoryStore.loadFromAgentContext(agentContext);

    assertThat(agentContextMemoryStore.getMessages(DEFAULT_MEMORY_ID))
        .isNotEmpty()
        .containsExactlyElementsOf(testMessages());
  }

  @Test
  void loadsMessagesFromAgentContextWithCustomMemoryId() throws Exception {
    System.out.println(LocalDateTime.now());

    assertThat(agentContextMemoryStore.getMessages(TEST_MEMORY_ID)).isEmpty();

    AgentContext agentContext = AgentContext.empty().withMemory(serializedTestMessages());

    agentContextMemoryStore.loadFromAgentContext(agentContext, TEST_MEMORY_ID);

    assertThat(agentContextMemoryStore.getMessages(TEST_MEMORY_ID))
        .isNotEmpty()
        .containsExactlyElementsOf(testMessages());
  }
}
