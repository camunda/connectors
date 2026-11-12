/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChatConversationTest {

  private static final ReasoningContent REASONING =
      ReasoningContent.reasoningContent("anthropic", Map.of("signature", "sig"));
  private static final ProviderContent PROVIDER_CONTENT =
      ProviderContent.providerContent("anthropic", Map.of("type", "server_tool_use"));
  private static final AgentDocumentContent AGENT_DOCUMENT =
      AgentDocumentContent.of(AgentDocumentId.of("doc-1"));
  private static final ToolCall TOOL_CALL = new ToolCall("call-1", "lookup", Map.of("id", 42));
  private static final List<ToolDefinition> TOOL_DEFINITIONS =
      List.of(ToolDefinition.builder().name("lookup").description("Looks up").build());

  @Test
  void holdsTheMessagesOfEachRoleWithTheirBlocksInOrder() {
    final var messages =
        List.<Message>of(
            SystemMessage.builder().content(List.of(textContent("a"), textContent("b"))).build(),
            UserMessage.builder()
                .content(List.of(textContent("hi"), ObjectContent.objectContent(Map.of("k", 1))))
                .build(),
            AssistantMessage.builder()
                .content(List.of(REASONING, textContent("calling"), PROVIDER_CONTENT))
                .toolCalls(List.of(TOOL_CALL))
                .messageId("msg_1")
                .metadata(Map.of("phase", "commentary"))
                .build(),
            ToolCallResultMessage.builder()
                .results(
                    List.of(
                        ToolCallResultContent.builder()
                            .id("call-1")
                            .name("lookup")
                            .content(List.of(textContent("found")))
                            .build()))
                .build());

    final var conversation = ChatConversation.of(messages, TOOL_DEFINITIONS);

    assertThat(conversation)
        .isEqualTo(
            new ChatConversation(
                List.of(
                    new ChatSystemMessage(List.of(textContent("a"), textContent("b"))),
                    new ChatUserMessage(
                        List.of(textContent("hi"), ObjectContent.objectContent(Map.of("k", 1)))),
                    new ChatAssistantMessage(
                        List.of(REASONING, textContent("calling"), PROVIDER_CONTENT),
                        List.of(TOOL_CALL),
                        "msg_1",
                        Map.of("phase", "commentary")),
                    new ChatToolCallResultMessage(
                        List.of(
                            new ChatToolCallResult(
                                "call-1", "lookup", List.of(textContent("found")))))),
                TOOL_DEFINITIONS));
    assertThat(conversation.leadingSystemMessage())
        .hasValueSatisfying(system -> assertThat(system.text()).isEqualTo("a\nb"));
  }

  @Test
  void hasNoLeadingSystemMessageWhenTheFirstMessageIsNoSystemMessage() {
    final var conversation =
        ChatConversation.of(
            List.of(UserMessage.builder().content(List.of(textContent("hi"))).build()), List.of());

    assertThat(conversation.leadingSystemMessage()).isEmpty();
  }

  static Stream<Arguments> blocksWithoutAFormInTheirMessage() {
    return Stream.of(
        rejected("A user message", userMessage(AGENT_DOCUMENT), "AgentDocumentContent"),
        rejected("A user message", userMessage(REASONING), "ReasoningContent"),
        rejected("A user message", userMessage(PROVIDER_CONTENT), "ProviderContent"),
        rejected("A tool call result", toolCallResult(AGENT_DOCUMENT), "AgentDocumentContent"),
        rejected("A tool call result", toolCallResult(REASONING), "ReasoningContent"),
        rejected("A tool call result", toolCallResult(PROVIDER_CONTENT), "ProviderContent"),
        rejected(
            "An assistant message",
            AssistantMessage.builder().content(List.of(AGENT_DOCUMENT)).build(),
            "AgentDocumentContent"),
        rejected(
            "A system message",
            SystemMessage.builder()
                .content(List.of(ObjectContent.objectContent(Map.of("k", 1))))
                .build(),
            "ObjectContent"));
  }

  @ParameterizedTest
  @MethodSource("blocksWithoutAFormInTheirMessage")
  void failsForABlockThatTheMessageOfAChatRequestCannotHold(
      String holder, Message message, String blockType) {
    assertThatThrownBy(() -> ChatConversation.of(List.of(message), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "%s of a chat request cannot hold content of type '%s'".formatted(holder, blockType));
  }

  private static Arguments rejected(String holder, Message message, String blockType) {
    return Arguments.of(holder, Named.of(holder + " with " + blockType, message), blockType);
  }

  private static Message userMessage(Content block) {
    return UserMessage.builder().content(List.of(block)).build();
  }

  private static Message toolCallResult(Content block) {
    return ToolCallResultMessage.builder()
        .results(
            List.of(
                ToolCallResultContent.builder()
                    .id("call-1")
                    .name("lookup")
                    .content(List.of(block))
                    .build()))
        .build();
  }
}
