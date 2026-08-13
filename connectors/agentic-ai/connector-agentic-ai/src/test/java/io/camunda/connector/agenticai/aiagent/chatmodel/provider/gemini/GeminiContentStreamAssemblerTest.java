/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.genai.ResponseStream;
import com.google.genai.types.BlockedReason;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ResponseStream} cannot be constructed without a live HTTP {@link
 * com.google.genai.ApiResponse}, so the streamed chunks are fed in through a Mockito mock stubbing
 * only {@link ResponseStream#iterator()} (the sole method the assembler uses) - the same approach
 * {@code AnthropicMessageStreamAssemblerTest} takes for the Anthropic SDK's {@code StreamResponse}.
 * Chunks themselves are real SDK types built through their AutoValue builders.
 */
class GeminiContentStreamAssemblerTest {

  private static final byte[] THOUGHT_SIGNATURE = "signature".getBytes(StandardCharsets.UTF_8);

  private final GeminiContentStreamAssembler assembler = new GeminiContentStreamAssemblerImpl();

  @Test
  void mergesConsecutiveTextPartsAcrossChunks() {
    final GenerateContentResponse assembled =
        assembler.assemble(
            streamOf(chunk(textPart("Hello")), chunk(textPart(", ")), chunk(textPart("world!"))));

    assertThat(partsOf(assembled))
        .singleElement()
        .satisfies(part -> assertThat(part.text()).contains("Hello, world!"));
    assertThat(contentOf(assembled).role()).contains("model");
  }

  @Test
  void keepsThoughtAndTextRunsSeparate() {
    final GenerateContentResponse assembled =
        assembler.assemble(
            streamOf(
                chunk(thoughtPart("Let me ")),
                chunk(thoughtPart("think.")),
                chunk(textPart("The ")),
                chunk(textPart("answer."))));

    assertThat(partsOf(assembled))
        .satisfiesExactly(
            thought -> {
              assertThat(thought.thought()).contains(true);
              assertThat(thought.text()).contains("Let me think.");
            },
            text -> {
              assertThat(text.thought()).isEmpty();
              assertThat(text.text()).contains("The answer.");
            });
  }

  @Test
  void doesNotMergeTextRunsSeparatedByANonTextPart() {
    final GenerateContentResponse assembled =
        assembler.assemble(
            streamOf(
                chunk(textPart("before")),
                chunk(functionCallPart("myTool")),
                chunk(textPart("after"))));

    assertThat(partsOf(assembled))
        .satisfiesExactly(
            first -> assertThat(first.text()).contains("before"),
            second -> assertThat(second.functionCall()).isPresent(),
            third -> assertThat(third.text()).contains("after"));
  }

  @Test
  void passesFunctionCallPartsThroughUnmodifiedInArrivalOrder() {
    final Part first = functionCallPart("firstTool");
    final Part second = functionCallPart("secondTool");

    final GenerateContentResponse assembled =
        assembler.assemble(streamOf(chunk(first), chunk(second)));

    assertThat(partsOf(assembled)).containsExactly(first, second);
  }

  @Test
  void mergesThoughtRunAndCarriesThoughtSignatureOver() {
    final GenerateContentResponse assembled =
        assembler.assemble(
            streamOf(
                chunk(thoughtPart("thin")),
                chunk(
                    thoughtPart("king").toBuilder().thoughtSignature(THOUGHT_SIGNATURE).build())));

    assertThat(partsOf(assembled))
        .singleElement()
        .satisfies(
            part -> {
              assertThat(part.text()).contains("thinking");
              assertThat(part.thoughtSignature().orElseThrow()).isEqualTo(THOUGHT_SIGNATURE);
            });
  }

  @Test
  void startsANewRunAfterAPartCarryingAThoughtSignature() {
    final GenerateContentResponse assembled =
        assembler.assemble(
            streamOf(
                chunk(thoughtPart("first").toBuilder().thoughtSignature(THOUGHT_SIGNATURE).build()),
                chunk(thoughtPart("second"))));

    assertThat(partsOf(assembled))
        .satisfiesExactly(
            signed -> {
              assertThat(signed.text()).contains("first");
              assertThat(signed.thoughtSignature().orElseThrow()).isEqualTo(THOUGHT_SIGNATURE);
            },
            unsigned -> {
              assertThat(unsigned.text()).contains("second");
              assertThat(unsigned.thoughtSignature()).isEmpty();
            });
  }

  @Test
  void takesUsageMetadataAndFinishReasonFromTheFinalChunk() {
    final GenerateContentResponse intermediate =
        chunk(textPart("partial")).toBuilder()
            .usageMetadata(
                GenerateContentResponseUsageMetadata.builder()
                    .promptTokenCount(10)
                    .candidatesTokenCount(2)
                    .totalTokenCount(12))
            .build();
    final GenerateContentResponse last =
        GenerateContentResponse.builder()
            .candidates(
                Candidate.builder().finishReason(new FinishReason(FinishReason.Known.MAX_TOKENS)))
            .usageMetadata(
                GenerateContentResponseUsageMetadata.builder()
                    .promptTokenCount(10)
                    .candidatesTokenCount(42)
                    .totalTokenCount(52))
            .build();

    final GenerateContentResponse assembled = assembler.assemble(streamOf(intermediate, last));

    final GenerateContentResponseUsageMetadata usage = assembled.usageMetadata().orElseThrow();
    assertThat(usage.candidatesTokenCount()).contains(42);
    assertThat(usage.totalTokenCount()).contains(52);
    assertThat(candidateOf(assembled).finishReason().orElseThrow().knownEnum())
        .isEqualTo(FinishReason.Known.MAX_TOKENS);
    assertThat(partsOf(assembled))
        .singleElement()
        .satisfies(part -> assertThat(part.text()).contains("partial"));
  }

  @Test
  void passesPromptFeedbackThroughWhenNoCandidatesArePresent() {
    final GenerateContentResponsePromptFeedback promptFeedback =
        GenerateContentResponsePromptFeedback.builder()
            .blockReason(new BlockedReason(BlockedReason.Known.SAFETY))
            .blockReasonMessage("blocked by safety filters")
            .build();

    final GenerateContentResponse assembled =
        assembler.assemble(
            streamOf(GenerateContentResponse.builder().promptFeedback(promptFeedback).build()));

    assertThat(assembled.promptFeedback()).contains(promptFeedback);
    assertThat(assembled.candidates()).isEmpty();
  }

  @Test
  void assemblesASingleChunkStream() {
    final GenerateContentResponse single =
        chunk(textPart("all at once"), functionCallPart("myTool")).toBuilder()
            .modelVersion("gemini-test-model")
            .responseId("response-1")
            .build();

    final GenerateContentResponse assembled = assembler.assemble(streamOf(single));

    assertThat(partsOf(assembled))
        .satisfiesExactly(
            text -> assertThat(text.text()).contains("all at once"),
            functionCall ->
                assertThat(functionCall.functionCall().orElseThrow().name()).contains("myTool"));
    assertThat(assembled.modelVersion()).contains("gemini-test-model");
    assertThat(assembled.responseId()).contains("response-1");
  }

  @Test
  void keepsFirstNonEmptyModelVersionAndResponseId() {
    final GenerateContentResponse first = chunk(textPart("a"));
    final GenerateContentResponse second =
        chunk(textPart("b")).toBuilder().modelVersion("gemini-test-model").responseId("r1").build();
    final GenerateContentResponse third =
        chunk(textPart("c")).toBuilder().modelVersion("other-model").responseId("r2").build();

    final GenerateContentResponse assembled = assembler.assemble(streamOf(first, second, third));

    assertThat(assembled.modelVersion()).contains("gemini-test-model");
    assertThat(assembled.responseId()).contains("r1");
  }

  @Test
  void usesTheFirstCandidateWhenAChunkCarriesMultiple() {
    final GenerateContentResponse multiCandidate =
        GenerateContentResponse.builder()
            .candidates(
                Candidate.builder()
                    .content(Content.builder().role("model").parts(textPart("first candidate"))),
                Candidate.builder()
                    .content(Content.builder().role("model").parts(textPart("second candidate"))))
            .build();

    final GenerateContentResponse assembled = assembler.assemble(streamOf(multiCandidate));

    assertThat(assembled.candidates().orElseThrow()).hasSize(1);
    assertThat(partsOf(assembled))
        .singleElement()
        .satisfies(part -> assertThat(part.text()).contains("first candidate"));
  }

  @Test
  void doesNotCloseTheStream() {
    final ResponseStream<GenerateContentResponse> stream = streamOf(chunk(textPart("text")));

    assembler.assemble(stream);

    verify(stream, never()).close();
  }

  @Test
  void throwsOnAnEmptyStream() {
    final ResponseStream<GenerateContentResponse> stream = streamOf();

    assertThatThrownBy(() -> assembler.assemble(stream))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no chunks");
  }

  private static ResponseStream<GenerateContentResponse> streamOf(
      GenerateContentResponse... chunks) {
    @SuppressWarnings("unchecked")
    final ResponseStream<GenerateContentResponse> stream = mock(ResponseStream.class);
    when(stream.iterator()).thenReturn(List.of(chunks).iterator());
    return stream;
  }

  private static GenerateContentResponse chunk(Part... parts) {
    return GenerateContentResponse.builder()
        .candidates(
            Candidate.builder().content(Content.builder().role("model").parts(parts).build()))
        .build();
  }

  private static Part textPart(String text) {
    return Part.builder().text(text).build();
  }

  private static Part thoughtPart(String text) {
    return Part.builder().thought(true).text(text).build();
  }

  private static Part functionCallPart(String name) {
    return Part.builder()
        .functionCall(FunctionCall.builder().name(name).args(Map.of("arg", "value")).build())
        .build();
  }

  private static Candidate candidateOf(GenerateContentResponse response) {
    return response.candidates().orElseThrow().get(0);
  }

  private static Content contentOf(GenerateContentResponse response) {
    return candidateOf(response).content().orElseThrow();
  }

  private static List<Part> partsOf(GenerateContentResponse response) {
    return contentOf(response).parts().orElseThrow();
  }
}
