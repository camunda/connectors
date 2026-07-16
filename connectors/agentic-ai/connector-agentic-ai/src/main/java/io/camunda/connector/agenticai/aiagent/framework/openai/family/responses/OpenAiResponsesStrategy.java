/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.responses;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.openai.family.OpenAiApiFamilyStrategy;
import java.time.Duration;

/**
 * {@link OpenAiApiFamilyStrategy} for the OpenAI Responses API: builds the {@link
 * ResponseCreateParams} via {@link OpenAiResponsesRequestConverter}, drives the vendor SDK's
 * streaming Responses endpoint uniformly (mirroring the Anthropic sibling's approach of always
 * streaming to accumulate the same shape the non-streaming API would return), and translates the
 * assembled {@link Response} back to the domain {@link ChatModelResult} via {@link
 * OpenAiResponsesResponseConverter}.
 */
public class OpenAiResponsesStrategy implements OpenAiApiFamilyStrategy {

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
  public ChatModelResult call(
      OpenAIClient client,
      ChatModelRequest request,
      OpenAiModelCapabilities capabilities,
      boolean modelMatched) {
    final ResponseCreateParams params =
        requestConverter.toResponseCreateParams(
            request.executionContext(), request.snapshot(), capabilities, modelMatched);

    final long startNanos = System.nanoTime();
    final Response response;
    try (StreamResponse<ResponseStreamEvent> stream = client.responses().createStreaming(params)) {
      response = streamAssembler.assemble(stream);
    }
    final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
    return responseConverter.toResult(response, executionTime);
  }
}
