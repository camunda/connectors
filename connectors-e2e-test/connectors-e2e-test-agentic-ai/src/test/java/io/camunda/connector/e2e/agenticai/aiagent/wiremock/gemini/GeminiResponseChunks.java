/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.BlockedReason;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds single Gemini streaming chunks as real SDK {@link GenerateContentResponse} instances, so
 * that {@link StreamingGeminiChatModelStubs} can serialize them with the vendor SDK's own mapper
 * instead of hand-written JSON string literals. Every field name on the wire therefore comes from
 * the SDK's own {@code @JsonProperty} annotations rather than from a guess.
 *
 * <p>One chunk equals one {@code data: {...}} SSE line. A realistic Gemini turn is a sequence of
 * them: text arrives incrementally across several chunks, and the <em>final</em> chunk is the one
 * carrying {@code finishReason} and the cumulative {@code usageMetadata} (which is exactly what
 * {@code GeminiContentStreamAssemblerImpl} relies on: last-wins for usage, last candidate for
 * candidate-level metadata, text runs concatenated).
 *
 * <p>A candidate is emitted only when the chunk has parts or a finish reason. That rule is what
 * makes a blocked-prompt chunk ({@link Builder#blockReason}) come out with <b>no {@code candidates}
 * key at all</b> — the shape {@code GeminiContentResponseConverter} keys its blocked-prompt
 * handling on, and the shape real Gemini returns when input-side filtering rejects a prompt.
 */
public final class GeminiResponseChunks {

  /** Role Gemini reports on an assistant candidate's content. */
  private static final String ROLE_MODEL = "model";

  private static final ObjectMapper ARGUMENTS_MAPPER = new ObjectMapper();

  private GeminiResponseChunks() {}

  public static Builder chunk() {
    return new Builder();
  }

  /**
   * A complete single-chunk turn: text plus {@code STOP} and usage, i.e. what a non-streaming
   * response would have looked like. Convenience for tests that care about something other than
   * chunking.
   */
  public static GenerateContentResponse text(String text, int promptTokens, int candidateTokens) {
    return chunk()
        .text(text)
        .finishReason(FinishReason.Known.STOP)
        .usage(promptTokens, candidateTokens)
        .build();
  }

  /**
   * A complete single-chunk turn carrying optional leading text plus one or more {@code
   * functionCall} parts. Gemini reports {@code STOP} even for a tool-calling turn (it has no
   * tool-use finish reason), which is precisely the vendor quirk {@code
   * GeminiContentResponseConverter} overrides to {@code TOOL_USE}.
   */
  public static GenerateContentResponse toolCalls(
      @Nullable String text,
      int promptTokens,
      int candidateTokens,
      List<ToolCallStub> toolCalls,
      boolean includeFunctionCallIds) {
    final Builder builder = chunk();
    if (text != null && !text.isBlank()) {
      builder.text(text);
    }
    for (final ToolCallStub toolCall : toolCalls) {
      builder.functionCall(toolCall, includeFunctionCallIds);
    }
    return builder
        .finishReason(FinishReason.Known.STOP)
        .usage(promptTokens, candidateTokens)
        .build();
  }

  /** Base64-encodes a signature the way Gemini reports {@code thoughtSignature} on the wire. */
  public static String encodeSignature(String rawSignature) {
    return Base64.getEncoder().encodeToString(rawSignature.getBytes(StandardCharsets.UTF_8));
  }

  public static final class Builder {

    private final List<Part> parts = new ArrayList<>();
    // Both nullable. Annotating a qualified nested type requires the FinishReason.@Nullable Known
    // form, which is not worth the noise on a private field in a test builder.
    private FinishReason.Known finishReason;
    private BlockedReason.Known blockReason;
    private @Nullable String blockReasonMessage;
    private @Nullable GenerateContentResponseUsageMetadata usageMetadata;

    private Builder() {}

    /** A plain answer-text part. */
    public Builder text(String text) {
      parts.add(Part.builder().text(text).build());
      return this;
    }

    /** An answer-text part carrying a {@code thoughtSignature} (base64 on the wire). */
    public Builder text(String text, String signatureBase64) {
      parts.add(
          Part.builder().text(text).thoughtSignature(decodeSignature(signatureBase64)).build());
      return this;
    }

    /** A {@code thought}-flagged text part, i.e. streamed reasoning. */
    public Builder thought(String text) {
      parts.add(Part.builder().text(text).thought(true).build());
      return this;
    }

    /**
     * A {@code functionCall} part. {@code includeId} chooses between the two real Gemini surfaces:
     * the Developer API leaves {@code FunctionCall.id} unset (the converter then synthesizes a
     * UUID), while Vertex AI populates it.
     */
    public Builder functionCall(ToolCallStub toolCall, boolean includeId) {
      final var functionCall =
          FunctionCall.builder().name(toolCall.name()).args(parseArguments(toolCall));
      if (includeId) {
        functionCall.id(toolCall.id());
      }
      parts.add(Part.builder().functionCall(functionCall.build()).build());
      return this;
    }

    /**
     * A {@code functionCall} part carrying the {@code thoughtSignature} Gemini 3 stamps on a call
     * it thought about — the value the request converter must replay on the follow-up request.
     */
    public Builder functionCall(ToolCallStub toolCall, boolean includeId, String signatureBase64) {
      functionCall(toolCall, includeId);
      final int lastIndex = parts.size() - 1;
      parts.set(
          lastIndex,
          parts.get(lastIndex).toBuilder()
              .thoughtSignature(decodeSignature(signatureBase64))
              .build());
      return this;
    }

    public Builder finishReason(FinishReason.Known finishReason) {
      this.finishReason = finishReason;
      return this;
    }

    /** Prompt-token / candidate-token counts, the two the domain metrics always report. */
    public Builder usage(int promptTokens, int candidateTokens) {
      this.usageMetadata =
          GenerateContentResponseUsageMetadata.builder()
              .promptTokenCount(promptTokens)
              .candidatesTokenCount(candidateTokens)
              .totalTokenCount(promptTokens + candidateTokens)
              .build();
      return this;
    }

    /**
     * Usage including the thinking-specific {@code thoughtsTokenCount} and the implicit-cache
     * {@code cachedContentTokenCount}, which map to the domain reasoning / cache-read counters.
     */
    public Builder usage(
        int promptTokens, int candidateTokens, int thoughtsTokens, int cachedTokens) {
      this.usageMetadata =
          GenerateContentResponseUsageMetadata.builder()
              .promptTokenCount(promptTokens)
              .candidatesTokenCount(candidateTokens)
              .thoughtsTokenCount(thoughtsTokens)
              .cachedContentTokenCount(cachedTokens)
              .totalTokenCount(promptTokens + candidateTokens + thoughtsTokens)
              .build();
      return this;
    }

    /**
     * Marks this chunk as a blocked prompt: it then carries only {@code promptFeedback} and no
     * candidate whatsoever (see the class javadoc).
     */
    public Builder blockReason(BlockedReason.Known blockReason) {
      this.blockReason = blockReason;
      return this;
    }

    public Builder blockReasonMessage(String blockReasonMessage) {
      this.blockReasonMessage = blockReasonMessage;
      return this;
    }

    public GenerateContentResponse build() {
      final var response = GenerateContentResponse.builder();
      if (usageMetadata != null) {
        response.usageMetadata(usageMetadata);
      }
      if (blockReason != null) {
        final var promptFeedback =
            GenerateContentResponsePromptFeedback.builder().blockReason(blockReason);
        if (blockReasonMessage != null) {
          promptFeedback.blockReasonMessage(blockReasonMessage);
        }
        response.promptFeedback(promptFeedback.build());
      }

      // No parts and no finish reason means there is nothing candidate-shaped to report: leave
      // `candidates` off entirely rather than emitting an empty shell.
      if (!parts.isEmpty() || finishReason != null) {
        final var candidate = Candidate.builder().index(0);
        if (!parts.isEmpty()) {
          candidate.content(Content.builder().role(ROLE_MODEL).parts(parts).build());
        }
        if (finishReason != null) {
          candidate.finishReason(finishReason);
        }
        response.candidates(List.of(candidate.build()));
      }

      return response.build();
    }
  }

  private static byte[] decodeSignature(String signatureBase64) {
    return Base64.getDecoder().decode(signatureBase64);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseArguments(ToolCallStub toolCall) {
    try {
      return ARGUMENTS_MAPPER.readValue(toolCall.argumentsJson(), Map.class);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to parse stubbed tool call arguments: " + toolCall.argumentsJson(), e);
    }
  }
}
