/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.JsonSerializable;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentParameters;
import com.google.genai.types.GenerateContentResponse;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.wire.LoggingSupport;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gemini {@link ChatModel}: drives the vendor SDK's streaming {@code generateContentStream} for
 * every call, accumulates the chunks via a {@link GeminiContentStreamAssembler} into the single
 * {@link GenerateContentResponse} shape a non-streaming call would have returned, then delegates to
 * {@link GeminiContentRequestConverter} and {@link GeminiContentResponseConverter} to translate
 * to/from the domain model.
 *
 * <p>The {@link Client} is built once by the factory and owned for the lifetime of this instance
 * (one agent request, across all continuation rounds); {@link #close()} closes it once.
 */
public class GeminiChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(GeminiChatModel.class);
  private static final ObjectMapper MAPPER = JsonSerializable.objectMapper();

  private final Client client;
  private final GeminiConfiguration configuration;
  private final GeminiContentRequestConverter requestConverter;
  private final GeminiContentResponseConverter responseConverter;
  private final GeminiContentStreamAssembler streamAssembler;

  public GeminiChatModel(
      Client client,
      GeminiConfiguration configuration,
      GeminiContentRequestConverter requestConverter,
      GeminiContentResponseConverter responseConverter) {
    this.client = client;
    this.configuration = configuration;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = new GeminiContentStreamAssemblerImpl();
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    final GenerateContentConfig config =
        requestConverter.toGenerateContentConfig(
            configuration,
            request.executionContext().configuration().response(),
            request.snapshot());
    final List<Content> contents = requestConverter.toContents(request.snapshot());

    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "Gemini generateContent request: {}",
          LoggingSupport.toJson(
              MAPPER,
              GenerateContentParameters.builder()
                  .model(configuration.model())
                  .contents(contents)
                  .config(config)
                  .build()));
    }

    final GenerateContentResponse response;
    try (ResponseStream<GenerateContentResponse> stream =
        client.models.generateContentStream(configuration.model(), contents, config)) {
      response = streamAssembler.assemble(stream);
    }
    if (LOG.isTraceEnabled()) {
      LOG.trace("Gemini generateContent response: {}", LoggingSupport.toJson(MAPPER, response));
    }
    return responseConverter.toResult(response);
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.error("Failed to close Gemini Client", e);
    }
  }
}
