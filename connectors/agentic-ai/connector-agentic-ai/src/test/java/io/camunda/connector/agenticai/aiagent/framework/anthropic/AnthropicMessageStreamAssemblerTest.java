/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicMessageStreamAssembler.describeEvent;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaCacheCreation;
import com.anthropic.models.beta.messages.BetaContainer;
import com.anthropic.models.beta.messages.BetaContextManagementResponse;
import com.anthropic.models.beta.messages.BetaDiagnostics;
import com.anthropic.models.beta.messages.BetaDirectCaller;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageDeltaUsage;
import com.anthropic.models.beta.messages.BetaOutputTokensDetails;
import com.anthropic.models.beta.messages.BetaRawContentBlockDeltaEvent;
import com.anthropic.models.beta.messages.BetaRawContentBlockStartEvent;
import com.anthropic.models.beta.messages.BetaRawContentBlockStopEvent;
import com.anthropic.models.beta.messages.BetaRawMessageDeltaEvent;
import com.anthropic.models.beta.messages.BetaRawMessageStartEvent;
import com.anthropic.models.beta.messages.BetaRawMessageStopEvent;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaRefusalStopDetails;
import com.anthropic.models.beta.messages.BetaServerToolUsage;
import com.anthropic.models.beta.messages.BetaServerToolUseBlock;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaUsage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnthropicMessageStreamAssembler#describeEvent(BetaRawMessageStreamEvent)},
 * the compact metadata-only TRACE-logging helper. Builds real vendor SDK {@link
 * BetaRawMessageStreamEvent} instances (the same builder idiom used in {@code
 * AnthropicMessageResponseConverterTest} and the native Anthropic SSE wiremock stubs) to verify
 * each variant's description contains its discriminating metadata and never a payload body.
 */
class AnthropicMessageStreamAssemblerTest {

  @Test
  void describesMessageStart() {
    final BetaMessage message = messageShell("msg-test-1", "claude-test-model");
    final BetaRawMessageStreamEvent event =
        BetaRawMessageStreamEvent.ofMessageStart(
            BetaRawMessageStartEvent.builder().message(message).build());

    final String description = describeEvent(event);

    assertThat(description)
        .contains("message_start")
        .contains("msg-test-1")
        .contains("claude-test-model");
  }

  @Test
  void describesContentBlockStartForServerToolUseBlock() {
    final BetaRawMessageStreamEvent event =
        BetaRawMessageStreamEvent.ofContentBlockStart(
            BetaRawContentBlockStartEvent.builder()
                .contentBlock(
                    BetaServerToolUseBlock.builder()
                        .id("srvtoolu_1")
                        .name(BetaServerToolUseBlock.Name.CODE_EXECUTION)
                        .caller(BetaDirectCaller.builder().build())
                        .input(JsonValue.from(Map.of("code", "print(1)")))
                        .build())
                .index(2)
                .build());

    final String description = describeEvent(event);

    assertThat(description)
        .contains("content_block_start")
        .contains("server_tool_use")
        .contains("2");
    assertThat(description).doesNotContain("print(1)");
  }

  @Test
  void describesContentBlockDelta() {
    final BetaRawMessageStreamEvent event =
        BetaRawMessageStreamEvent.ofContentBlockDelta(
            BetaRawContentBlockDeltaEvent.builder()
                .inputJsonDelta("{\"secret\":42}")
                .index(1)
                .build());

    final String description = describeEvent(event);

    assertThat(description)
        .contains("content_block_delta")
        .contains("input_json_delta")
        .contains("1");
    assertThat(description).doesNotContain("secret").doesNotContain("42");
  }

  @Test
  void describesContentBlockStop() {
    final BetaRawMessageStreamEvent event =
        BetaRawMessageStreamEvent.ofContentBlockStop(
            BetaRawContentBlockStopEvent.builder().index(3).build());

    final String description = describeEvent(event);

    assertThat(description).contains("content_block_stop").contains("3");
  }

  @Test
  void describesMessageDeltaWithStopReasonAndUsage() {
    final BetaRawMessageStreamEvent event =
        BetaRawMessageStreamEvent.ofMessageDelta(
            BetaRawMessageDeltaEvent.builder()
                .contextManagement((BetaContextManagementResponse) null)
                .delta(
                    BetaRawMessageDeltaEvent.Delta.builder()
                        .container((BetaContainer) null)
                        .stopReason(BetaStopReason.TOOL_USE)
                        .stopDetails((BetaRefusalStopDetails) null)
                        .stopSequence((String) null)
                        .build())
                .usage(
                    BetaMessageDeltaUsage.builder()
                        .cacheCreationInputTokens((Long) null)
                        .cacheReadInputTokens((Long) null)
                        .inputTokens(10)
                        .iterations(List.of())
                        .outputTokens(25)
                        .outputTokensDetails((BetaOutputTokensDetails) null)
                        .serverToolUse((BetaServerToolUsage) null)
                        .build())
                .build());

    final String description = describeEvent(event);

    assertThat(description).contains("message_delta").contains("tool_use").contains("25");
  }

  @Test
  void describesMessageStop() {
    final BetaRawMessageStreamEvent event =
        BetaRawMessageStreamEvent.ofMessageStop(BetaRawMessageStopEvent.builder().build());

    final String description = describeEvent(event);

    assertThat(description).contains("message_stop");
  }

  @Test
  void describesUnrecognizedVariantWithGenericFallbackInsteadOfThrowing() {
    // "ping" is a real Anthropic SSE event type (a keep-alive) that this SDK version's
    // BetaRawMessageStreamEvent union has no dedicated variant for; deserializing it exercises
    // the same "unknown" fallback path a genuinely new future event type would take.
    final BetaRawMessageStreamEvent event = deserialize("{\"type\":\"ping\"}");

    assertThat(event.isMessageStart()).isFalse();
    assertThat(event.isContentBlockStart()).isFalse();
    assertThat(event.isContentBlockDelta()).isFalse();
    assertThat(event.isContentBlockStop()).isFalse();
    assertThat(event.isMessageDelta()).isFalse();
    assertThat(event.isMessageStop()).isFalse();

    final String description = describeEvent(event);

    assertThat(description).contains("unknown");
  }

  private static BetaRawMessageStreamEvent deserialize(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, BetaRawMessageStreamEvent.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to deserialize BetaRawMessageStreamEvent fixture", e);
    }
  }

  private static BetaMessage messageShell(String id, String model) {
    return BetaMessage.builder()
        .id(id)
        .container((BetaContainer) null)
        .content(List.of())
        .contextManagement((BetaContextManagementResponse) null)
        .diagnostics((BetaDiagnostics) null)
        .model(model)
        .stopDetails((BetaRefusalStopDetails) null)
        .stopReason((BetaStopReason) null)
        .stopSequence((String) null)
        .usage(
            BetaUsage.builder()
                .inputTokens(1)
                .outputTokens(0)
                .cacheCreation((BetaCacheCreation) null)
                .cacheCreationInputTokens((Long) null)
                .cacheReadInputTokens((Long) null)
                .inferenceGeo((String) null)
                .iterations(List.of())
                .outputTokensDetails((BetaOutputTokensDetails) null)
                .serverToolUse((BetaServerToolUsage) null)
                .serviceTier((BetaUsage.ServiceTier) null)
                .speed((BetaUsage.Speed) null)
                .build())
        .build();
  }
}
