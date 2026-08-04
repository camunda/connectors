/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.util.LoggingSupport;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link OpenAiApiFamilyStrategy} for the OpenAI Chat Completions API: builds the {@link
 * ChatCompletionCreateParams} via {@link OpenAiCompletionsRequestConverter}, drives the vendor
 * SDK's streaming Chat Completions endpoint uniformly (mirroring the Anthropic sibling's approach
 * of always streaming to accumulate the same shape the non-streaming API would return), and
 * translates the assembled {@link ChatCompletion} back to the domain {@link ChatResult} via {@link
 * OpenAiCompletionsResponseConverter}.
 */
public class OpenAiCompletionsStrategy implements OpenAiApiFamilyStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompletionsStrategy.class);
  private static final ObjectMapper MAPPER = ObjectMappers.jsonMapper();

  private final OpenAiCompletionsRequestConverter requestConverter;
  private final OpenAiCompletionsResponseConverter responseConverter;
  private final OpenAiCompletionsStreamAssembler streamAssembler;

  public OpenAiCompletionsStrategy(
      OpenAiCompletionsRequestConverter requestConverter,
      OpenAiCompletionsResponseConverter responseConverter,
      OpenAiCompletionsStreamAssembler streamAssembler) {
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
  }

  @Override
  public ChatResult call(
      OpenAIClient client, OpenAiChatModelConfiguration configuration, ChatRequest request) {
    final ChatCompletionCreateParams params =
        requestConverter.toRequest(
            configuration,
            request.executionContext().configuration().response(),
            request.snapshot());
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "OpenAI Chat Completions API request: {}", LoggingSupport.toJson(MAPPER, params._body()));
    }

    final long startNanos = System.nanoTime();
    final ChatCompletion completion;
    try (StreamResponse<ChatCompletionChunk> stream =
        client.chat().completions().createStreaming(params)) {
      completion = streamAssembler.assemble(stream);
    }
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "OpenAI Chat Completions API response: {}", LoggingSupport.toJson(MAPPER, completion));
    }
    final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
    return responseConverter.toResult(completion, executionTime);
  }
}
