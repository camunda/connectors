/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnthropicOnFoundryResponseMapperTest {

  private final AnthropicOnFoundryResponseMapper mapper = new AnthropicOnFoundryResponseMapper();

  // -------------------------------------------------------------------------
  // Test fixture helpers — SDK builders are strict about required fields
  // -------------------------------------------------------------------------

  /** Build a minimal Usage with all required fields set. */
  private static Usage usage(long inputTokens, long outputTokens) {
    return Usage.builder()
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)
        .cacheCreation(Optional.empty())
        .cacheCreationInputTokens(Optional.empty())
        .cacheReadInputTokens(Optional.empty())
        .inferenceGeo(Optional.empty())
        .serverToolUse(Optional.empty())
        .serviceTier(Optional.empty())
        .build();
  }

  /** Build a minimal TextBlock with all required fields set. */
  private static TextBlock textBlock(String text) {
    return TextBlock.builder().text(text).citations(Optional.empty()).build();
  }

  /** Build a minimal ToolUseBlock with all required fields set. */
  private static ToolUseBlock toolUseBlock(String id, String name, JsonValue input) {
    return ToolUseBlock.builder()
        .id(id)
        .name(name)
        .input(input)
        .caller(ToolUseBlock.Caller.ofDirect(DirectCaller.builder().build()))
        .build();
  }

  /** Build a Message with all required fields set. */
  private static Message message(
      String id, List<ContentBlock> blocks, StopReason stopReason, Usage usageValue) {
    var builder =
        Message.builder()
            .id(id)
            .model(Model.CLAUDE_SONNET_4_0)
            .stopReason(Optional.of(stopReason))
            .stopDetails(Optional.empty())
            .stopSequence(Optional.empty())
            .usage(usageValue);
    for (ContentBlock block : blocks) {
      builder.addContent(block);
    }
    return builder.build();
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  void translatesTextResponseToAiMessage() {
    // given
    var contentBlock = ContentBlock.ofText(textBlock("Hello, world!"));
    var msg = message("msg_001", List.of(contentBlock), StopReason.END_TURN, usage(10, 5));

    // when
    ChatResponse response = mapper.toChatResponse(msg);

    // then
    assertThat(response.aiMessage().text()).isEqualTo("Hello, world!");
    assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    TokenUsage tokenUsage = response.tokenUsage();
    assertThat(tokenUsage.inputTokenCount()).isEqualTo(10);
    assertThat(tokenUsage.outputTokenCount()).isEqualTo(5);
    assertThat(tokenUsage.totalTokenCount()).isEqualTo(15);
  }

  @Test
  void translatesToolUseResponseToToolExecutionRequests() {
    // given
    var toolUseBlock =
        toolUseBlock(
            "toolu_01XFDUDYJgAACTU9bRTQE47m",
            "get_weather",
            JsonValue.from(java.util.Map.of("location", "San Francisco, CA")));
    var contentBlock = ContentBlock.ofToolUse(toolUseBlock);
    var msg = message("msg_002", List.of(contentBlock), StopReason.TOOL_USE, usage(20, 8));

    // when
    ChatResponse response = mapper.toChatResponse(msg);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    AiMessage aiMessage = response.aiMessage();
    assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
    assertThat(toolRequests).hasSize(1);
    ToolExecutionRequest toolRequest = toolRequests.get(0);
    assertThat(toolRequest.id()).isEqualTo("toolu_01XFDUDYJgAACTU9bRTQE47m");
    assertThat(toolRequest.name()).isEqualTo("get_weather");
    assertThat(toolRequest.arguments()).contains("San Francisco, CA");
  }

  @Test
  void translatesMaxTokensStopReasonToLength() {
    // given
    var contentBlock = ContentBlock.ofText(textBlock("Partial response..."));
    var msg = message("msg_003", List.of(contentBlock), StopReason.MAX_TOKENS, usage(15, 1024));

    // when
    ChatResponse response = mapper.toChatResponse(msg);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
    assertThat(response.aiMessage().text()).isEqualTo("Partial response...");
  }
}
