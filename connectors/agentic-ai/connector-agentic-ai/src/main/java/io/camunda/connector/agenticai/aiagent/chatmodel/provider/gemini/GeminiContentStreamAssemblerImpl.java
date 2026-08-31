/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges Gemini's streamed {@link GenerateContentResponse} chunks into one response:
 *
 * <ul>
 *   <li><b>Candidates</b>: only the first candidate of each chunk is considered (the connector
 *       never requests more than one); the synthesized response carries exactly that one candidate.
 *       It is based on the <b>last</b> candidate seen, so candidate-level metadata (finish reason,
 *       token count, safety ratings) is the final chunk's — which is where Gemini reports it.
 *   <li><b>Text parts</b>: consecutive text parts sharing the same {@code thought} flag form one
 *       "run" and are concatenated across chunks, since Gemini streams text incrementally. A part
 *       carrying a {@code thoughtSignature} closes its run (signatures belong to exactly one part
 *       and must round-trip verbatim, so they are never conflated); an incoming signature is
 *       carried onto the merged part.
 *   <li><b>Other parts</b> ({@code functionCall}, {@code inlineData}, {@code fileData}, ...) are
 *       complete units per chunk and are appended in arrival order, never merged field-wise.
 *   <li><b>Usage metadata</b>: last chunk that reports it wins (Gemini reports cumulative counts on
 *       the final chunk); never summed across chunks.
 *   <li><b>{@code promptFeedback}</b> (blocked prompt, typically the only chunk and without any
 *       candidate): passed through unmodified, first chunk reporting it wins. The synthesized
 *       response then has no candidates at all, which is what the response converter keys on.
 *   <li><b>{@code modelVersion}/{@code responseId}/content role</b>: first non-empty value wins.
 * </ul>
 */
public class GeminiContentStreamAssemblerImpl implements GeminiContentStreamAssembler {

  private static final Logger LOG = LoggerFactory.getLogger(GeminiContentStreamAssemblerImpl.class);

  @Override
  public GenerateContentResponse assemble(ResponseStream<GenerateContentResponse> stream) {
    final List<Part> parts = new ArrayList<>();
    Candidate lastCandidate = null;
    String role = null;
    String modelVersion = null;
    String responseId = null;
    GenerateContentResponseUsageMetadata usageMetadata = null;
    GenerateContentResponsePromptFeedback promptFeedback = null;
    boolean hasChunks = false;

    for (final GenerateContentResponse chunk : stream) {
      hasChunks = true;

      if (modelVersion == null) {
        modelVersion = chunk.modelVersion().filter(value -> !value.isBlank()).orElse(null);
      }
      if (responseId == null) {
        responseId = chunk.responseId().filter(value -> !value.isBlank()).orElse(null);
      }
      if (promptFeedback == null) {
        promptFeedback = chunk.promptFeedback().orElse(null);
      }
      usageMetadata = chunk.usageMetadata().orElse(usageMetadata);

      final Candidate candidate =
          chunk
              .candidates()
              .filter(candidates -> !candidates.isEmpty())
              .map(List::getFirst)
              .orElse(null);
      if (candidate == null) {
        continue;
      }
      lastCandidate = candidate;

      final Content content = candidate.content().orElse(null);
      if (content == null) {
        continue;
      }
      if (role == null) {
        role = content.role().filter(value -> !value.isBlank()).orElse(null);
      }
      content.parts().orElse(List.of()).forEach(part -> appendPart(parts, part));
    }

    if (!hasChunks) {
      throw new IllegalStateException("Gemini streaming response contained no chunks");
    }

    final GenerateContentResponse.Builder response = GenerateContentResponse.builder();
    if (modelVersion != null) {
      response.modelVersion(modelVersion);
    }
    if (responseId != null) {
      response.responseId(responseId);
    }
    if (usageMetadata != null) {
      response.usageMetadata(usageMetadata);
    }
    if (promptFeedback != null) {
      response.promptFeedback(promptFeedback);
    }
    if (lastCandidate != null) {
      final Candidate.Builder candidate = lastCandidate.toBuilder();
      if (!parts.isEmpty()) {
        final Content.Builder content = Content.builder().parts(parts);
        if (role != null) {
          content.role(role);
        }
        candidate.content(content.build());
      }
      response.candidates(List.of(candidate.build()));
    }

    return response.build();
  }

  private static void appendPart(List<Part> parts, Part part) {
    warnOnPartialFunctionCall(part);

    if (!parts.isEmpty() && part.text().isPresent()) {
      final int lastIndex = parts.size() - 1;
      final Part accumulated = parts.get(lastIndex);
      if (isMergeableWith(accumulated, part)) {
        parts.set(lastIndex, merge(accumulated, part));
        return;
      }
    }

    parts.add(part);
  }

  private static boolean isMergeableWith(Part accumulated, Part incoming) {
    return accumulated.text().isPresent()
        && accumulated.thoughtSignature().isEmpty()
        && isThought(accumulated) == isThought(incoming);
  }

  private static Part merge(Part accumulated, Part incoming) {
    final Part.Builder merged =
        accumulated.toBuilder().text(accumulated.text().orElse("") + incoming.text().orElse(""));
    incoming.thoughtSignature().ifPresent(merged::thoughtSignature);
    return merged.build();
  }

  private static boolean isThought(Part part) {
    return part.thought().orElse(false);
  }

  /**
   * Function-call arguments arrive whole for the Gemini Developer API: each SSE chunk deserializes
   * into a complete {@link GenerateContentResponse}, and {@code FunctionCall.args()} is a parsed
   * map, so a fragment cannot be represented. The SDK does model incremental arguments via {@code
   * FunctionCall.partialArgs()}/{@code willContinue()}, but documents both as unsupported on this
   * API. Rather than guessing at an unverified merge strategy, make the case visible if it ever
   * shows up.
   */
  private static void warnOnPartialFunctionCall(Part part) {
    part.functionCall()
        .filter(
            functionCall ->
                functionCall.partialArgs().isPresent() || functionCall.willContinue().isPresent())
        .ifPresent(
            functionCall ->
                LOG.warn(
                    "Gemini streamed a partial function call (partialArgs/willContinue set) for '{}'. Partial function-call arguments are not merged, the resulting tool call may be incomplete.",
                    functionCall.name().orElse("<unknown>")));
  }
}
