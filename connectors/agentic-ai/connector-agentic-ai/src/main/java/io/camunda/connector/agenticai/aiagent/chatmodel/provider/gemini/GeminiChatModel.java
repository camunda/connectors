/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.JsonSerializable;
import com.google.genai.ResponseStream;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentParameters;
import com.google.genai.types.GenerateContentResponse;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.util.LoggingSupport;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
  private final GeminiChatModelConfiguration configuration;
  private final GeminiContentRequestConverter requestConverter;
  private final GeminiContentResponseConverter responseConverter;
  private final GeminiContentStreamAssembler streamAssembler;

  public GeminiChatModel(
      Client client,
      GeminiChatModelConfiguration configuration,
      GeminiContentRequestConverter requestConverter,
      GeminiContentResponseConverter responseConverter) {
    this(
        client,
        configuration,
        requestConverter,
        responseConverter,
        new GeminiContentStreamAssemblerImpl());
  }

  GeminiChatModel(
      Client client,
      GeminiChatModelConfiguration configuration,
      GeminiContentRequestConverter requestConverter,
      GeminiContentResponseConverter responseConverter,
      GeminiContentStreamAssembler streamAssembler) {
    this.client = client;
    this.configuration = configuration;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
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

    final long startNanos = System.nanoTime();
    try {
      final GenerateContentResponse response;
      try (ResponseStream<GenerateContentResponse> stream =
          client.models.generateContentStream(configuration.model(), contents, config)) {
        response = streamAssembler.assemble(stream);
      }
      if (LOG.isTraceEnabled()) {
        LOG.trace("Gemini generateContent response: {}", LoggingSupport.toJson(MAPPER, response));
      }
      final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
      return responseConverter.toResult(response, executionTime);
    } catch (ChatModelRejectedException e) {
      // Rethrown unwrapped so the catch-all below does not flatten it into a generic
      // ConnectorException.
      throw e;
    } catch (ApiException e) {
      if (isContextWindowExceeded(e)) {
        throw new ContextWindowExceededException(
            "Model's context window was exceeded before it could finish generating a response.",
            e,
            null);
      }
      final String status =
          Optional.ofNullable(e.status())
              .filter(s -> !s.isBlank())
              .orElseGet(() -> e.getClass().getSimpleName());
      final String message =
          Optional.ofNullable(e.message())
              .filter(m -> !m.isBlank())
              .orElseGet(() -> e.getClass().getSimpleName());
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Model call failed with HTTP %d (%s): %s".formatted(e.code(), status, message),
          e);
    } catch (Exception e) {
      final String detail =
          Optional.ofNullable(e.getMessage())
              .filter(m -> !m.isBlank())
              .orElseGet(() -> e.getClass().getSimpleName());
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "Model call failed: %s".formatted(detail), e);
    }
  }

  /**
   * Gemini has no dedicated exception type or error code for an over-length prompt -- {@code
   * ApiException.code()}/{@code status()} (400, {@code INVALID_ARGUMENT}) are shared with many
   * unrelated validation failures, so detection falls back to matching this specific, stable
   * substring of the message text. Confirmed identical across the Developer API and Vertex AI
   * backends; the surrounding token counts vary per request and are excluded from the match.
   */
  private boolean isContextWindowExceeded(ApiException e) {
    return e.code() == 400
        && e.message() != null
        && e.message().contains("exceeds the maximum number of tokens allowed");
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.warn("Failed to close Gemini Client", e);
    }
  }
}
