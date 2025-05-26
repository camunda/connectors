/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import static io.camunda.connector.agenticai.model.message.UserMessage.userMessage;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageWindowRuntimeMemoryTest {

  private static final List<Message> TEST_MESSAGES = TestMessagesFixture.testMessages();
  private static final Integer MAX_MESSAGES = 8;

  private RuntimeMemory delegateMemory;
  private RuntimeMemory memory;

  @BeforeEach
  void setUp() {
    delegateMemory = new DefaultRuntimeMemory();
    memory = new MessageWindowRuntimeMemory(delegateMemory, MAX_MESSAGES);
  }

  @Test
  void delegatesAddingSingleMessages() {
    TEST_MESSAGES.forEach(memory::addMessage);

    assertThat(memory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(memory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);

    assertThat(delegateMemory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(delegateMemory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);
  }

  @Test
  void delegatesAddingListOfMessages() {
    memory.addMessages(TEST_MESSAGES);

    assertThat(memory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(memory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);

    assertThat(delegateMemory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(delegateMemory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);
  }

  @Test
  void delegatesClearingMessages() {
    memory.addMessages(TEST_MESSAGES);

    assertThat(memory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(memory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);

    assertThat(delegateMemory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(delegateMemory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);

    memory.clear();

    assertThat(memory.allMessages()).isEmpty();
    assertThat(memory.filteredMessages()).isEmpty();

    assertThat(delegateMemory.allMessages()).isEmpty();
    assertThat(delegateMemory.filteredMessages()).isEmpty();
  }

  @Test
  void filtersOldestMessagesWhenMaxMessagesExceeded() {
    List<Message> messages = new ArrayList<>();
    for (int i = 1; i <= MAX_MESSAGES + 2; i++) {
      messages.add(userMessage("Message " + i));
    }

    memory.addMessages(messages);

    assertThat(memory.allMessages()).hasSize(MAX_MESSAGES + 2).containsExactlyElementsOf(messages);
    assertThat(memory.filteredMessages()).hasSize(MAX_MESSAGES);
    assertThat(((UserMessage) memory.filteredMessages().getFirst()).content())
        .containsExactly(TextContent.textContent("Message 3"));
    assertThat(((UserMessage) memory.filteredMessages().getLast()).content())
        .containsExactly(TextContent.textContent("Message 10"));

    // delegate memory should not be affected
    assertThat(delegateMemory.allMessages()).containsExactlyElementsOf(messages);
    assertThat(delegateMemory.filteredMessages()).containsExactlyElementsOf(messages);
  }

  @Test
  void doesNotFilterSystemMessage() {
    SystemMessage systemMessage = SystemMessage.systemMessage("System message");

    List<Message> messages = new ArrayList<>();
    messages.add(systemMessage);
    for (int i = 1; i <= MAX_MESSAGES + 2; i++) {
      messages.add(userMessage("Message " + i));
    }

    memory.addMessages(messages);

    assertThat(memory.allMessages()).hasSize(MAX_MESSAGES + 3).containsExactlyElementsOf(messages);
    assertThat(memory.filteredMessages()).hasSize(MAX_MESSAGES);
    assertThat(memory.filteredMessages().getFirst()).isEqualTo(systemMessage);
    assertThat(((UserMessage) memory.filteredMessages().get(1)).content())
        .containsExactly(TextContent.textContent("Message 4"));
    assertThat(((UserMessage) memory.filteredMessages().getLast()).content())
        .containsExactly(TextContent.textContent("Message 10"));

    // delegate memory should not be affected
    assertThat(delegateMemory.allMessages()).containsExactlyElementsOf(messages);
    assertThat(delegateMemory.filteredMessages()).containsExactlyElementsOf(messages);
  }

  @Test
  void removesOrphanedToolCallResultMessages() {
    final List<Message> messages =
        List.of(
            SystemMessage.systemMessage("You are a helpful assistant. Be nice."),
            UserMessage.userMessage(
                    List.of(
                        textContent("What is the weather in Munich?"),
                        textContent("Is it typical for this time of the year?")))
                .withName("user1"),
            AssistantMessage.assistantMessage(
                "To give an answer, I need to first look up the weather in Munich. Considering available tools, I should call the getWeather tool. In addition I will call the getDateTime tool to know the current date and time.",
                List.of(
                    ToolCall.builder()
                        .id("abcdef")
                        .name("getWeather")
                        .arguments(Map.of("location", "MUC"))
                        .build(),
                    ToolCall.builder().id("fedcba").name("getDateTime").build())),
            ToolCallResultMessage.toolCallResultMessage(
                List.of(
                    ToolCallResult.builder()
                        .id("abcdef")
                        .name("getWeather")
                        .content("Sunny, 22°C")
                        .build(),
                    ToolCallResult.builder()
                        .id("fedcba")
                        .name("getDateTime")
                        .content(
                            Map.of(
                                "date",
                                "2025-04-14",
                                "time",
                                "15:56:50",
                                "iso",
                                "2025-04-14T15:56:50"))
                        .build())),
            AssistantMessage.assistantMessage(
                    "The weather in Munich is sunny with a temperature of 22°C. This is typical for April.")
                .withMetadata(Map.of("some", "value")),
            UserMessage.userMessage("Thank you!").withName("user1"));

    memory.addMessages(messages);
    memory.addMessage(userMessage("User message 1"));
    memory.addMessage(userMessage("User message 2"));

    assertThat(memory.filteredMessages()).hasSize(MAX_MESSAGES);
    assertThat(memory.filteredMessages().get(1)).isEqualTo(messages.get(1));

    // evict first message
    memory.addMessage(userMessage("User message 3"));
    assertThat(memory.filteredMessages()).hasSize(MAX_MESSAGES);
    assertThat(memory.filteredMessages().get(1)).isEqualTo(messages.get(2));

    // evict assistant message with tool call requests -> also remove tool call result messages
    memory.addMessage(userMessage("User message 4"));
    assertThat(memory.filteredMessages()).hasSize(MAX_MESSAGES - 1);
    assertThat(memory.filteredMessages().get(1)).isEqualTo(messages.get(4));
  }
}
