/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.util.JsonPayloadLogging;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anthropic {@link ChatModel}: drives the vendor SDK's streaming Messages endpoint for every call
 * (Anthropic has no meaningful non-streaming distinction for this connector's purposes; streaming
 * is used uniformly to accumulate the same {@link Message} shape the non-streaming API would
 * return), then delegates to the Task 3/4/5 converters to translate to/from the domain model.
 *
 * <p>The {@link AnthropicClient} is built once by the factory and owned for the lifetime of this
 * instance (one agent request, across all continuation rounds); {@link #close()} closes it once.
 * {@link AnthropicClient#close()} is deliberately not exercised through try-with-resources: the
 * vendor SDK's {@code AnthropicClient} interface does not implement {@link AutoCloseable} (its
 * {@code close()} is a plain, unchecked method the SDK explicitly documents as usually unnecessary
 * to call). {@link StreamResponse}, in contrast, does implement {@code AutoCloseable} and is closed
 * via try-with-resources on every call.
 */
public class AnthropicChatModelApi implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicChatModelApi.class);
  private static final ObjectMapper MAPPER = ObjectMappers.jsonMapper();

  private final AnthropicClient client;
  private final AnthropicMessageRequestConverter requestConverter;
  private final AnthropicMessageResponseConverter responseConverter;
  private final AnthropicMessageStreamAssembler streamAssembler;

  public AnthropicChatModelApi(
      AnthropicClient client,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter) {
    this(
        client,
        requestConverter,
        responseConverter,
        AnthropicMessageStreamAssembler.accumulating());
  }

  AnthropicChatModelApi(
      AnthropicClient client,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter,
      AnthropicMessageStreamAssembler streamAssembler) {
    this.client = client;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    final MessageCreateParams params =
        requestConverter.toMessageCreateParams(request.executionContext(), request.snapshot());
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "Anthropic Messages API request: {}", JsonPayloadLogging.toJson(MAPPER, params._body()));
    }

    final long startNanos = System.nanoTime();
    try {
      final Message message;
      try (StreamResponse<RawMessageStreamEvent> stream =
          client.messages().createStreaming(params)) {
        message = streamAssembler.assemble(stream);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(
            "Anthropic Messages API response: {}", JsonPayloadLogging.toJson(MAPPER, message));
      }
      final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
      return responseConverter.toResult(message, executionTime);
    } catch (Exception e) {
      final String detail =
          Optional.ofNullable(e.getMessage())
              .filter(m -> !m.isBlank())
              .orElseGet(() -> e.getClass().getSimpleName());
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "Model call failed: %s".formatted(detail), e);
    }
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.warn("Failed to close AnthropicClient", e);
    }
  }
}
