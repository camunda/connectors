/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.util.LoggingSupport;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link OpenAiApiFamilyStrategy} for the OpenAI Responses API: builds the {@link
 * ResponseCreateParams} via {@link OpenAiResponsesRequestConverter}, drives the vendor SDK's
 * streaming Responses endpoint uniformly (always streaming to accumulate the same shape the
 * non-streaming API would return), and translates the assembled {@link Response} back to the domain
 * {@link ChatResult} via {@link OpenAiResponsesResponseConverter}.
 */
public class OpenAiResponsesStrategy implements OpenAiApiFamilyStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiResponsesStrategy.class);
  private static final ObjectMapper MAPPER = ObjectMappers.jsonMapper();

  private final OpenAiResponsesRequestConverter requestConverter;
  private final OpenAiResponsesResponseConverter responseConverter;
  private final OpenAiResponsesStreamAssembler streamAssembler;

  public OpenAiResponsesStrategy(
      OpenAiResponsesRequestConverter requestConverter,
      OpenAiResponsesResponseConverter responseConverter,
      OpenAiResponsesStreamAssembler streamAssembler) {
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
  }

  @Override
  public ChatResult call(
      OpenAIClient client, OpenAiChatModelConfiguration configuration, ChatRequest request) {
    final ResponseCreateParams params =
        requestConverter.toRequest(
            configuration,
            request.executionContext().configuration().response(),
            request.snapshot());
    if (LOG.isTraceEnabled()) {
      LOG.trace("OpenAI Responses API request: {}", LoggingSupport.toJson(MAPPER, params._body()));
    }

    final long startNanos = System.nanoTime();
    final Response response;
    try (StreamResponse<ResponseStreamEvent> stream = client.responses().createStreaming(params)) {
      response = streamAssembler.assemble(stream);
    }
    if (LOG.isTraceEnabled()) {
      LOG.trace("OpenAI Responses API response: {}", LoggingSupport.toJson(MAPPER, response));
    }
    final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
    return responseConverter.toResult(response, executionTime);
  }
}
