/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.CitationsDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseType;

/**
 * Feeds synthesised {@code ConverseStream} event sequences directly into a {@link
 * BedrockConverseStreamAssembler} (bypassing the actual EventStream binary framing, which is the
 * AWS SDK's own responsibility, not this assembler's) and asserts the resulting {@link
 * software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse}.
 */
class BedrockConverseStreamAssemblerTest {

  private final BedrockConverseStreamAssembler assembler =
      new BedrockConverseStreamAssembler(new ObjectMapper());

  private static ContentBlockStartEvent contentBlockStart(int index) {
    return ContentBlockStartEvent.builder().contentBlockIndex(index).build();
  }

  private static ContentBlockStartEvent toolUseContentBlockStart(
      int index, String toolUseId, String name) {
    return ContentBlockStartEvent.builder()
        .contentBlockIndex(index)
        .start(
            ContentBlockStart.fromToolUse(
                ToolUseBlockStart.builder().toolUseId(toolUseId).name(name).build()))
        .build();
  }

  private static ContentBlockDeltaEvent textDelta(int index, String text) {
    return ContentBlockDeltaEvent.builder()
        .contentBlockIndex(index)
        .delta(ContentBlockDelta.builder().text(text).build())
        .build();
  }

  private static ContentBlockDeltaEvent toolUseInputDelta(int index, String inputFragment) {
    return ContentBlockDeltaEvent.builder()
        .contentBlockIndex(index)
        .delta(
            ContentBlockDelta.builder()
                .toolUse(ToolUseBlockDelta.builder().input(inputFragment).build())
                .build())
        .build();
  }

  private static ContentBlockDeltaEvent reasoningTextDelta(int index, String text) {
    return ContentBlockDeltaEvent.builder()
        .contentBlockIndex(index)
        .delta(
            ContentBlockDelta.builder()
                .reasoningContent(ReasoningContentBlockDelta.fromText(text))
                .build())
        .build();
  }

  private static ContentBlockDeltaEvent reasoningSignatureDelta(int index, String signature) {
    return ContentBlockDeltaEvent.builder()
        .contentBlockIndex(index)
        .delta(
            ContentBlockDelta.builder()
                .reasoningContent(ReasoningContentBlockDelta.fromSignature(signature))
                .build())
        .build();
  }

  private static ContentBlockDeltaEvent reasoningRedactedContentDelta(int index, String bytes) {
    return ContentBlockDeltaEvent.builder()
        .contentBlockIndex(index)
        .delta(
            ContentBlockDelta.builder()
                .reasoningContent(
                    ReasoningContentBlockDelta.fromRedactedContent(SdkBytes.fromUtf8String(bytes)))
                .build())
        .build();
  }

  private static ContentBlockDeltaEvent citationDelta(int index, String title, String source) {
    return ContentBlockDeltaEvent.builder()
        .contentBlockIndex(index)
        .delta(
            ContentBlockDelta.builder()
                .citation(CitationsDelta.builder().title(title).source(source).build())
                .build())
        .build();
  }

  private static ContentBlockStopEvent contentBlockStop(int index) {
    return ContentBlockStopEvent.builder().contentBlockIndex(index).build();
  }

  @Test
  void assemblesPlainTextSplitAcrossSeveralDeltas() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(contentBlockStart(0));
    assembler.visitContentBlockDelta(textDelta(0, "Hello"));
    assembler.visitContentBlockDelta(textDelta(0, ", "));
    assembler.visitContentBlockDelta(textDelta(0, "world!"));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.END_TURN).build());
    assembler.visitMetadata(
        ConverseStreamMetadataEvent.builder()
            .usage(TokenUsage.builder().inputTokens(10).outputTokens(5).build())
            .build());

    final var response = assembler.converseResponse();

    assertThat(response.output().message().role()).isEqualTo(ConversationRole.ASSISTANT);
    assertThat(response.output().message().content())
        .containsExactly(ContentBlock.fromText("Hello, world!"));
    assertThat(response.stopReason()).isEqualTo(StopReason.END_TURN);
    assertThat(response.usage().inputTokens()).isEqualTo(10);
    assertThat(response.usage().outputTokens()).isEqualTo(5);
  }

  @Test
  void assemblesToolUseInputArrivingAsThreeFragments() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(toolUseContentBlockStart(0, "tooluse_1", "get_weather"));
    assembler.visitContentBlockDelta(toolUseInputDelta(0, "{\"city\":"));
    assembler.visitContentBlockDelta(toolUseInputDelta(0, "\"Berlin\","));
    assembler.visitContentBlockDelta(toolUseInputDelta(0, "\"days\":3}"));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.TOOL_USE).build());

    final var response = assembler.converseResponse();

    assertThat(response.output().message().content()).hasSize(1);
    final var toolUse = response.output().message().content().get(0).toolUse();
    assertThat(toolUse.toolUseId()).isEqualTo("tooluse_1");
    assertThat(toolUse.name()).isEqualTo("get_weather");
    assertThat(toolUse.input().asMap().get("city").asString()).isEqualTo("Berlin");
    assertThat(toolUse.input().asMap().get("days").asNumber().stringValue()).isEqualTo("3");
  }

  @Test
  void assemblesToolUseWithNoInputFragmentsAsEmptyDocument() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(toolUseContentBlockStart(0, "tooluse_now", "now"));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.TOOL_USE).build());

    final var response = assembler.converseResponse();

    final var toolUse = response.output().message().content().get(0).toolUse();
    assertThat(toolUse.input().asMap()).isEmpty();
  }

  @Test
  void assemblesToolUseTypeFromContentBlockStart() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(
        ContentBlockStartEvent.builder()
            .contentBlockIndex(0)
            .start(
                ContentBlockStart.fromToolUse(
                    ToolUseBlockStart.builder()
                        .toolUseId("tooluse_1")
                        .name("web_search")
                        .type(ToolUseType.SERVER_TOOL_USE)
                        .build()))
            .build());
    assembler.visitContentBlockDelta(toolUseInputDelta(0, "{}"));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.TOOL_USE).build());

    final var response = assembler.converseResponse();

    final var toolUse = response.output().message().content().get(0).toolUse();
    assertThat(toolUse.typeAsString()).isEqualTo("server_tool_use");
  }

  @Test
  void assemblesReasoningBlockWithTextDeltasFollowedBySignatureDelta() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(contentBlockStart(0));
    assembler.visitContentBlockDelta(reasoningTextDelta(0, "Let me think "));
    assembler.visitContentBlockDelta(reasoningTextDelta(0, "about this."));
    assembler.visitContentBlockDelta(reasoningSignatureDelta(0, "sig-abc123"));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.END_TURN).build());

    final var response = assembler.converseResponse();

    assertThat(response.output().message().content()).hasSize(1);
    final var reasoningContent = response.output().message().content().get(0).reasoningContent();
    assertThat(reasoningContent.reasoningText().text()).isEqualTo("Let me think about this.");
    assertThat(reasoningContent.reasoningText().signature()).isEqualTo("sig-abc123");
    assertThat(reasoningContent.redactedContent()).isNull();
  }

  @Test
  void assemblesRedactedReasoningBlock() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(contentBlockStart(0));
    assembler.visitContentBlockDelta(reasoningRedactedContentDelta(0, "opaque-redacted-bytes"));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.END_TURN).build());

    final var response = assembler.converseResponse();

    assertThat(response.output().message().content()).hasSize(1);
    final var reasoningContent = response.output().message().content().get(0).reasoningContent();
    assertThat(reasoningContent.reasoningText()).isNull();
    assertThat(reasoningContent.redactedContent().asUtf8String())
        .isEqualTo("opaque-redacted-bytes");
  }

  @Test
  void assemblesCitationsContentBlockFromTextAndCitationDeltas() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(contentBlockStart(0));
    assembler.visitContentBlockDelta(textDelta(0, "Camunda 8 is a process orchestrator."));
    assembler.visitContentBlockDelta(citationDelta(0, "Camunda Docs", "https://docs.camunda.io"));
    assembler.visitContentBlockDelta(citationDelta(0, "Camunda Blog", "https://camunda.com/blog"));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.END_TURN).build());

    final var response = assembler.converseResponse();

    assertThat(response.output().message().content()).hasSize(1);
    final var citationsContent = response.output().message().content().get(0).citationsContent();
    assertThat(citationsContent.content()).hasSize(1);
    assertThat(citationsContent.content().get(0).text())
        .isEqualTo("Camunda 8 is a process orchestrator.");
    assertThat(citationsContent.citations()).hasSize(2);
    assertThat(citationsContent.citations().get(0).title()).isEqualTo("Camunda Docs");
    assertThat(citationsContent.citations().get(0).source()).isEqualTo("https://docs.camunda.io");
    assertThat(citationsContent.citations().get(1).title()).isEqualTo("Camunda Blog");
  }

  @Test
  void assemblesInterleavedContentBlockIndicesInIndexOrderRegardlessOfArrivalOrder() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());

    // Two blocks are opened before either is finished, and their deltas/stops arrive interleaved
    // and out of index order -- index 1 (a tool use) is started, appended to and stopped *before*
    // index 0 (plain text) is appended to and stopped.
    assembler.visitContentBlockStart(contentBlockStart(0));
    assembler.visitContentBlockStart(toolUseContentBlockStart(1, "tooluse_2", "get_time"));
    assembler.visitContentBlockDelta(toolUseInputDelta(1, "{}"));
    assembler.visitContentBlockDelta(textDelta(0, "before the tool call"));
    assembler.visitContentBlockStop(contentBlockStop(1));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.TOOL_USE).build());

    final var response = assembler.converseResponse();

    final var content = response.output().message().content();
    assertThat(content).hasSize(2);
    assertThat(content.get(0).text()).isEqualTo("before the tool call");
    assertThat(content.get(1).toolUse().name()).isEqualTo("get_time");
  }

  @Test
  void assemblesUsageAndMetricsArrivingInTheMetadataEvent() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(contentBlockStart(0));
    assembler.visitContentBlockDelta(textDelta(0, "ok"));
    assembler.visitContentBlockStop(contentBlockStop(0));
    assembler.visitMessageStop(MessageStopEvent.builder().stopReason(StopReason.END_TURN).build());
    assembler.visitMetadata(
        ConverseStreamMetadataEvent.builder()
            .usage(
                TokenUsage.builder()
                    .inputTokens(42)
                    .outputTokens(7)
                    .cacheReadInputTokens(2)
                    .cacheWriteInputTokens(1)
                    .build())
            .metrics(ConverseStreamMetrics.builder().latencyMs(1234L).build())
            .build());

    final var response = assembler.converseResponse();

    assertThat(response.usage().inputTokens()).isEqualTo(42);
    assertThat(response.usage().outputTokens()).isEqualTo(7);
    assertThat(response.usage().cacheReadInputTokens()).isEqualTo(2);
    assertThat(response.usage().cacheWriteInputTokens()).isEqualTo(1);
    assertThat(response.metrics().latencyMs()).isEqualTo(1234L);
  }

  @Test
  void throwsWhenConverseResponseCalledBeforeMessageStop() {
    assembler.visitMessageStart(
        MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
    assembler.visitContentBlockStart(contentBlockStart(0));
    assembler.visitContentBlockDelta(textDelta(0, "still streaming"));
    assembler.visitContentBlockStop(contentBlockStop(0));

    assertThatThrownBy(assembler::converseResponse).isInstanceOf(IllegalStateException.class);
  }
}
