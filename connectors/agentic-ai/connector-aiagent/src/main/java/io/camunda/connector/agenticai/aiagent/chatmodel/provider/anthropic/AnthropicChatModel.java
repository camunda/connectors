/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.util.LoggingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anthropic {@link ChatModel}: drives the vendor SDK's streaming Messages endpoint for every call
 * (Anthropic has no meaningful non-streaming distinction for this connector's purposes; streaming
 * is used uniformly to accumulate the same {@link Message} shape the non-streaming API would
 * return), then delegates to {@link AnthropicMessageRequestConverter} and {@link
 * AnthropicMessageResponseConverter} to translate to/from the domain model.
 *
 * <p>The {@link AnthropicClient} is built once by the factory and owned for the lifetime of this
 * instance (one agent request, across all continuation rounds); {@link #close()} closes it once.
 */
public class AnthropicChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicChatModel.class);
  private static final ObjectMapper MAPPER = ObjectMappers.jsonMapper();

  private final AnthropicConfiguration configuration;
  private final AnthropicClient client;
  private final AnthropicMessageRequestConverter requestConverter;
  private final AnthropicMessageResponseConverter responseConverter;
  private final AnthropicMessageStreamAssembler streamAssembler;

  public AnthropicChatModel(
      AnthropicClient client,
      AnthropicConfiguration configuration,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter) {
    this(
        client,
        configuration,
        requestConverter,
        responseConverter,
        AnthropicMessageStreamAssembler.accumulating());
  }

  AnthropicChatModel(
      AnthropicClient client,
      AnthropicConfiguration configuration,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter,
      AnthropicMessageStreamAssembler streamAssembler) {
    this.client = client;
    this.configuration = configuration;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    final MessageCreateParams params =
        requestConverter.toMessageCreateParams(
            configuration,
            request.executionContext().configuration().response(),
            request.snapshot());
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "Anthropic Messages API request: {}", LoggingSupport.toJson(MAPPER, params._body()));
    }

    final Message message;
    try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
      message = streamAssembler.assemble(stream);
    }
    if (LOG.isTraceEnabled()) {
      LOG.trace("Anthropic Messages API response: {}", LoggingSupport.toJson(MAPPER, message));
    }
    return responseConverter.toResult(message);
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.error("Failed to close AnthropicClient", e);
    }
  }
}
