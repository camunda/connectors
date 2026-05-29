/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.agentcoreharness;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessConversationRole;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolUseStatus;

class HarnessMessageConverterTest {

  private final HarnessMessageConverter converter = new HarnessMessageConverter();

  @ParameterizedTest
  @NullAndEmptySource
  void extractSystemPromptReturnsEmptyForNullOrEmpty(List<Message> messages) {
    var result = converter.extractSystemPrompt(messages != null ? messages : List.of());
    assertThat(result).isEmpty();
  }

  @Test
  void extractSystemPromptExtractsSystemMessages() {
    var messages =
        List.<Message>of(systemMessage("You are a helpful assistant."), userMessage("Hello"));

    var result = converter.extractSystemPrompt(messages);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).text()).isEqualTo("You are a helpful assistant.");
  }

  @Test
  void extractSystemPromptHandlesMultipleSystemMessages() {
    var messages =
        List.<Message>of(systemMessage("First instruction"), systemMessage("Second instruction"));

    var result = converter.extractSystemPrompt(messages);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).text()).isEqualTo("First instruction");
    assertThat(result.get(1).text()).isEqualTo("Second instruction");
  }

  @Test
  void toHarnessMessagesExcludesSystemMessages() {
    var messages = List.<Message>of(systemMessage("System prompt"), userMessage("User message"));

    var result = converter.toHarnessMessages(messages);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).role()).isEqualTo(HarnessConversationRole.USER);
  }

  @Test
  void toHarnessMessagesConvertsUserMessage() {
    var messages = List.<Message>of(userMessage("Hello, how are you?"));

    var result = converter.toHarnessMessages(messages);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).role()).isEqualTo(HarnessConversationRole.USER);
    assertThat(result.get(0).content()).hasSize(1);
    assertThat(result.get(0).content().get(0).text()).isEqualTo("Hello, how are you?");
  }

  @Test
  void toHarnessMessagesConvertsAssistantMessage() {
    var assistantMessage =
        AssistantMessage.builder()
            .content(List.of(TextContent.textContent("I'm doing well, thank you!")))
            .build();
    var messages = List.<Message>of(assistantMessage);

    var result = converter.toHarnessMessages(messages);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).role()).isEqualTo(HarnessConversationRole.ASSISTANT);
    assertThat(result.get(0).content().get(0).text()).isEqualTo("I'm doing well, thank you!");
  }

  @Test
  void toHarnessMessagesConvertsAssistantMessageWithToolCalls() {
    var toolCall =
        ToolCall.builder()
            .id("call_123")
            .name("get_weather")
            .arguments(Map.of("location", "Seattle"))
            .build();
    var assistantMessage =
        AssistantMessage.builder()
            .content(List.of(TextContent.textContent("Let me check the weather.")))
            .toolCalls(List.of(toolCall))
            .build();
    var messages = List.<Message>of(assistantMessage);

    var result = converter.toHarnessMessages(messages);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).content()).hasSize(2);
    // First content block is text
    assertThat(result.get(0).content().get(0).text()).isEqualTo("Let me check the weather.");
    // Second content block is tool use
    var toolUse = result.get(0).content().get(1).toolUse();
    assertThat(toolUse.toolUseId()).isEqualTo("call_123");
    assertThat(toolUse.name()).isEqualTo("get_weather");
  }

  @Test
  void toHarnessMessagesConvertsToolCallResultMessage() {
    var toolResult =
        ToolCallResult.builder().id("call_123").name("get_weather").content("Sunny, 72°F").build();
    var messages = List.<Message>of(toolCallResultMessage(toolResult));

    var result = converter.toHarnessMessages(messages);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).role()).isEqualTo(HarnessConversationRole.USER);
    var toolResultBlock = result.get(0).content().get(0).toolResult();
    assertThat(toolResultBlock.toolUseId()).isEqualTo("call_123");
    assertThat(toolResultBlock.status()).isEqualTo(HarnessToolUseStatus.SUCCESS);
  }

  @Test
  void toHarnessMessagesConvertsErrorToolCallResult() {
    var toolResult =
        ToolCallResult.builder()
            .id("call_456")
            .name("failing_tool")
            .content("Error occurred")
            .properties(Map.of(ToolCallResult.PROPERTY_INTERRUPTED, true))
            .build();
    var messages = List.<Message>of(toolCallResultMessage(toolResult));

    var result = converter.toHarnessMessages(messages);

    var toolResultBlock = result.get(0).content().get(0).toolResult();
    assertThat(toolResultBlock.status()).isEqualTo(HarnessToolUseStatus.ERROR);
  }

  @Test
  void toHarnessMessagesHandlesConversationFlow() {
    var messages =
        List.<Message>of(
            systemMessage("You are helpful."),
            userMessage("What's the weather?"),
            AssistantMessage.builder()
                .content(List.of(TextContent.textContent("Checking...")))
                .toolCalls(
                    List.of(
                        ToolCall.builder()
                            .id("call_1")
                            .name("get_weather")
                            .arguments(Map.of("location", "NYC"))
                            .build()))
                .build(),
            toolCallResultMessage(
                ToolCallResult.builder()
                    .id("call_1")
                    .name("get_weather")
                    .content("Rainy, 55°F")
                    .build()),
            AssistantMessage.builder()
                .content(List.of(TextContent.textContent("It's rainy in NYC.")))
                .build());

    var systemPrompt = converter.extractSystemPrompt(messages);
    var harnessMessages = converter.toHarnessMessages(messages);

    assertThat(systemPrompt).hasSize(1);
    assertThat(harnessMessages).hasSize(4); // user, assistant+tool, tool_result, assistant
    assertThat(harnessMessages.get(0).role()).isEqualTo(HarnessConversationRole.USER);
    assertThat(harnessMessages.get(1).role()).isEqualTo(HarnessConversationRole.ASSISTANT);
    assertThat(harnessMessages.get(2).role()).isEqualTo(HarnessConversationRole.USER);
    assertThat(harnessMessages.get(3).role()).isEqualTo(HarnessConversationRole.ASSISTANT);
  }

  // Helper methods to create messages
  private static SystemMessage systemMessage(String text) {
    return SystemMessage.builder().content(List.of(TextContent.textContent(text))).build();
  }

  private static UserMessage userMessage(String text) {
    return UserMessage.builder().content(List.of(TextContent.textContent(text))).build();
  }

  private static ToolCallResultMessage toolCallResultMessage(ToolCallResult result) {
    return ToolCallResultMessage.builder().results(List.of(result)).build();
  }
}
