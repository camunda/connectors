/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentResponse;

/**
 * Collapses a streamed Gemini response into the single {@link GenerateContentResponse} a
 * non-streaming {@code generateContent()} call would have returned, so the response converter only
 * ever deals with one response shape.
 *
 * <p>Unlike the Anthropic SDK, {@code com.google.genai} ships no chunk accumulator, so the merge is
 * hand-written: see {@link GeminiContentStreamAssemblerImpl}.
 *
 * <p>Implementations iterate the stream but never close it — the caller owns the stream (and closes
 * it via try-with-resources).
 */
@FunctionalInterface
public interface GeminiContentStreamAssembler {

  GenerateContentResponse assemble(ResponseStream<GenerateContentResponse> stream);
}
