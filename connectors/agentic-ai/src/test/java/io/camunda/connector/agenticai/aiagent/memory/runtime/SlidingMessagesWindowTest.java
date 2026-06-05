/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
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
import org.junit.jupiter.api.Test;

class SlidingMessagesWindowTest {

  private static final int MAX_MESSAGES = 8;
  private static final SlidingMessagesWindow WINDOW = SlidingMessagesWindow.ofSize(MAX_MESSAGES);

  @Test
  void rejectsNegativeMaxMessages() {
    assertThatThrownBy(() -> SlidingMessagesWindow.ofSize(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void usesDefaultWhenMemoryConfigurationIsNull() {
    assertThat(SlidingMessagesWindow.of(null).maxMessages())
        .isEqualTo(SlidingMessagesWindow.DEFAULT_MAX_MESSAGES);
  }

  @Test
  void usesDefaultWhenContextWindowSizeIsNull() {
    assertThat(SlidingMessagesWindow.of(new MemoryConfiguration(null, null)).maxMessages())
        .isEqualTo(SlidingMessagesWindow.DEFAULT_MAX_MESSAGES);
  }

  @Test
  void usesConfiguredContextWindowSize() {
    assertThat(SlidingMessagesWindow.of(new MemoryConfiguration(null, 5)).maxMessages())
        .isEqualTo(5);
  }

  @Test
  void returnsAllMessagesWhenUnderLimit() {
    List<Message> messages = new ArrayList<>();
    for (int i = 1; i <= MAX_MESSAGES; i++) {
      messages.add(userMessage("Message " + i));
    }

    assertThat(WINDOW.apply(messages)).containsExactlyElementsOf(messages);
  }

  @Test
  void filtersOldestMessagesWhenMaxMessagesExceeded() {
    List<Message> messages = new ArrayList<>();
    for (int i = 1; i <= MAX_MESSAGES + 2; i++) {
      messages.add(userMessage("Message " + i));
    }

    var result = WINDOW.apply(messages);

    assertThat(result).hasSize(MAX_MESSAGES);
    assertThat(((UserMessage) result.getFirst()).content())
        .containsExactly(TextContent.textContent("Message 3"));
    assertThat(((UserMessage) result.getLast()).content())
        .containsExactly(TextContent.textContent("Message 10"));
  }

  @Test
  void doesNotFilterSystemMessage() {
    SystemMessage systemMessage = systemMessage("System message");

    List<Message> messages = new ArrayList<>();
    messages.add(systemMessage);
    for (int i = 1; i <= MAX_MESSAGES + 2; i++) {
      messages.add(userMessage("Message " + i));
    }

    var result = WINDOW.apply(messages);

    assertThat(result).hasSize(MAX_MESSAGES);
    assertThat(result.getFirst()).isEqualTo(systemMessage);
    assertThat(((UserMessage) result.get(1)).content())
        .containsExactly(TextContent.textContent("Message 4"));
    assertThat(((UserMessage) result.getLast()).content())
        .containsExactly(TextContent.textContent("Message 10"));
  }

  @Test
  void removesOrphanedToolCallResultMessages() {
    final List<Message> messages =
        List.of(
            systemMessage("You are a helpful assistant. Be nice."),
            userMessage(
                    List.of(
                        textContent("What is the weather in Munich?"),
                        textContent("Is it typical for this time of the year?")))
                .withName("user1"),
            assistantMessage(
                "To give an answer, I need to first look up the weather in Munich.",
                List.of(
                    ToolCall.builder()
                        .id("abcdef")
                        .name("getWeather")
                        .arguments(Map.of("location", "MUC"))
                        .build(),
                    ToolCall.builder().id("fedcba").name("getDateTime").build())),
            toolCallResultMessage(
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
            assistantMessage("The weather in Munich is sunny with a temperature of 22°C.")
                .withMetadata(Map.of("some", "value")),
            userMessage("Thank you!").withName("user1"));

    List<Message> all = new ArrayList<>(messages);
    all.add(userMessage("User message 1"));
    all.add(userMessage("User message 2"));

    assertThat(WINDOW.apply(all)).hasSize(MAX_MESSAGES);
    assertThat(WINDOW.apply(all).get(1)).isEqualTo(messages.get(1));

    // evict first message
    all.add(userMessage("User message 3"));
    assertThat(WINDOW.apply(all)).hasSize(MAX_MESSAGES);
    assertThat(WINDOW.apply(all).get(1)).isEqualTo(messages.get(2));

    // evict assistant message with tool call requests -> also remove tool call result messages
    all.add(userMessage("User message 4"));
    assertThat(WINDOW.apply(all)).hasSize(MAX_MESSAGES - 1);
    assertThat(WINDOW.apply(all).get(1)).isEqualTo(messages.get(4));
  }

  @Test
  void doesNotCountToolCallDocumentMessagesTowardLimit() {
    final var documentUserMessage =
        UserMessage.builder()
            .content(
                List.of(
                    textContent(
                        "Documents extracted from tool calls (<doc /> tag + content pair):")))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    List<Message> messages = new ArrayList<>();
    for (int i = 1; i <= MAX_MESSAGES; i++) {
      messages.add(userMessage("Message " + i));
    }
    // insert document message in the middle — should not count toward limit
    messages.add(4, documentUserMessage);

    // all messages kept: document message doesn't count, so effective count is MAX_MESSAGES
    assertThat(WINDOW.apply(messages)).hasSize(MAX_MESSAGES + 1);
  }

  @Test
  void evictsDocumentUserMessageWithToolCallResult() {
    final var documentUserMessage =
        UserMessage.builder()
            .content(
                List.of(
                    textContent(
                        "Documents extracted from tool calls (<doc /> tag + content pair):")))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    final List<Message> messages =
        List.of(
            systemMessage("System"),
            userMessage("User 1"),
            assistantMessage(
                "Calling tools",
                List.of(ToolCall.builder().id("call_1").name("tool").arguments(Map.of()).build())),
            toolCallResultMessage(
                List.of(
                    ToolCallResult.builder().id("call_1").name("tool").content("result").build())),
            documentUserMessage,
            assistantMessage("Response"),
            userMessage("User 2"));

    List<Message> all = new ArrayList<>(messages);
    for (int i = 3; i <= 8; i++) {
      all.add(userMessage("User " + i));
    }

    var filtered = WINDOW.apply(all);
    assertThat(filtered).noneMatch(m -> m == documentUserMessage);
    assertThat(filtered)
        .noneMatch(
            m ->
                m instanceof ToolCallResultMessage tcr
                    && tcr.results().stream().anyMatch(r -> "call_1".equals(r.id())));
  }

  @Test
  void handlesOrphanedDocumentMessageDuringEviction() {
    final var documentUserMessage =
        UserMessage.builder()
            .content(
                List.of(
                    textContent(
                        "Documents extracted from tool calls (<doc /> tag + content pair):")))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    List<Message> messages = new ArrayList<>();
    messages.add(systemMessage("System"));
    // orphaned document message right after system message
    messages.add(documentUserMessage);
    for (int i = 1; i <= MAX_MESSAGES + 1; i++) {
      messages.add(userMessage("Message " + i));
    }

    var filtered = WINDOW.apply(messages);
    assertThat(filtered).noneMatch(m -> m == documentUserMessage);
    assertThat(filtered).hasSize(MAX_MESSAGES);
    assertThat(filtered.getFirst()).isEqualTo(systemMessage("System"));
  }

  @Test
  void doesNotMutateInputList() {
    List<Message> messages = new ArrayList<>();
    for (int i = 1; i <= MAX_MESSAGES + 2; i++) {
      messages.add(userMessage("Message " + i));
    }
    int originalSize = messages.size();

    WINDOW.apply(messages);

    assertThat(messages).hasSize(originalSize);
  }
}
