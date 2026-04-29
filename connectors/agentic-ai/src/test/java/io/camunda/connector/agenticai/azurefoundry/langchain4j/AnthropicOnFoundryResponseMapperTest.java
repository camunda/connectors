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
import org.mockito.Mockito;

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

  @Test
  void aggregatesMultipleTextBlocksInResponse() {
    // given — two consecutive text blocks; mapper concatenates with no separator
    var block1 = ContentBlock.ofText(textBlock("Hello "));
    var block2 = ContentBlock.ofText(textBlock("world."));
    var msg = message("msg_004", List.of(block1, block2), StopReason.END_TURN, usage(10, 5));

    // when
    ChatResponse response = mapper.toChatResponse(msg);

    // then
    assertThat(response.aiMessage().text()).isEqualTo("Hello world.");
    assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
  }

  @Test
  void translatesMixedTextAndToolUseResponse() {
    // given — text preamble followed by a tool_use block (Claude's typical pattern)
    var textContent = ContentBlock.ofText(textBlock("Let me check that for you."));
    var toolUseContent =
        ContentBlock.ofToolUse(
            toolUseBlock(
                "toolu_abc123",
                "get_weather",
                JsonValue.from(java.util.Map.of("location", "Paris"))));
    var msg =
        message(
            "msg_005", List.of(textContent, toolUseContent), StopReason.TOOL_USE, usage(20, 10));

    // when
    ChatResponse response = mapper.toChatResponse(msg);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    AiMessage aiMessage = response.aiMessage();
    assertThat(aiMessage.text()).isEqualTo("Let me check that for you.");
    assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
    ToolExecutionRequest req = aiMessage.toolExecutionRequests().get(0);
    assertThat(req.id()).isEqualTo("toolu_abc123");
    assertThat(req.name()).isEqualTo("get_weather");
    assertThat(req.arguments()).contains("Paris");
  }

  @Test
  void translatesMultipleParallelToolUseBlocks() {
    // given — two tool_use blocks in a single response (Claude parallel tool calls)
    var tool1 =
        ContentBlock.ofToolUse(
            toolUseBlock(
                "toolu_001",
                "get_weather",
                JsonValue.from(java.util.Map.of("location", "Berlin"))));
    var tool2 =
        ContentBlock.ofToolUse(
            toolUseBlock(
                "toolu_002",
                "get_time",
                JsonValue.from(java.util.Map.of("timezone", "Europe/Berlin"))));
    var msg = message("msg_006", List.of(tool1, tool2), StopReason.TOOL_USE, usage(25, 12));

    // when
    ChatResponse response = mapper.toChatResponse(msg);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    AiMessage aiMessage = response.aiMessage();
    // no text blocks → text field is null when tool requests are present
    assertThat(aiMessage.text()).isNull();
    assertThat(aiMessage.toolExecutionRequests()).hasSize(2);
    assertThat(aiMessage.toolExecutionRequests())
        .extracting(ToolExecutionRequest::id)
        .containsExactly("toolu_001", "toolu_002");
    assertThat(aiMessage.toolExecutionRequests())
        .extracting(ToolExecutionRequest::name)
        .containsExactly("get_weather", "get_time");
  }

  @Test
  void translatesStopSequenceStopReasonToStop() {
    // given — stop_sequence is treated identically to end_turn
    var contentBlock = ContentBlock.ofText(textBlock("Done."));
    var msg = message("msg_007", List.of(contentBlock), StopReason.STOP_SEQUENCE, usage(8, 3));

    // when
    ChatResponse response = mapper.toChatResponse(msg);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    assertThat(response.aiMessage().text()).isEqualTo("Done.");
  }

  @Test
  void mapsNullStopReasonToOther() {
    // given — stopReason() returns Optional.empty() (requires mocking; SDK builder mandates a
    // value)
    Message message = Mockito.mock(Message.class);
    Mockito.when(message.content()).thenReturn(List.of(ContentBlock.ofText(textBlock("hi"))));
    Mockito.when(message.stopReason()).thenReturn(Optional.empty());
    Mockito.when(message.usage()).thenReturn(usage(1, 1));
    Mockito.when(message.id()).thenReturn("msg_008");
    Mockito.when(message.model()).thenReturn(Model.CLAUDE_SONNET_4_0);

    // when
    ChatResponse response = mapper.toChatResponse(message);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.OTHER);
  }

  @Test
  void mapsUnknownStopReasonToOther() {
    // given — REFUSAL is a known SDK value the mapper does not handle explicitly
    var contentBlock = ContentBlock.ofText(textBlock("I cannot help with that."));
    var msg = message("msg_009", List.of(contentBlock), StopReason.REFUSAL, usage(5, 4));

    // when
    ChatResponse response = mapper.toChatResponse(msg);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.OTHER);
  }
}
