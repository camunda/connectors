/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native Anthropic {@link ChatModelApi}: drives the vendor SDK's streaming Messages endpoint for
 * every call (Anthropic has no meaningful non-streaming distinction for this connector's purposes;
 * streaming is used uniformly to accumulate the same {@link Message} shape the non-streaming API
 * would return), then delegates to the Task 3/4 converters to translate to/from the domain model.
 *
 * <p>{@link AnthropicClient#close()} is deliberately not exercised through try-with-resources: the
 * vendor SDK's {@code AnthropicClient} interface does not implement {@link AutoCloseable} (its
 * {@code close()} is a plain, unchecked method the SDK explicitly documents as usually unnecessary
 * to call). {@link StreamResponse}, in contrast, does implement {@code AutoCloseable} and is closed
 * via try-with-resources.
 */
public class AnthropicChatModelApi implements ChatModelApi {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicChatModelApi.class);

  private final AnthropicClientFactory clientFactory;
  private final AnthropicMessageRequestConverter requestConverter;
  private final AnthropicMessageResponseConverter responseConverter;
  private final ModelCapabilities capabilities;
  private final AnthropicMessageStreamAssembler streamAssembler;

  public AnthropicChatModelApi(
      AnthropicClientFactory clientFactory,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter,
      ModelCapabilities capabilities) {
    this(
        clientFactory,
        requestConverter,
        responseConverter,
        capabilities,
        AnthropicMessageStreamAssembler.accumulating());
  }

  AnthropicChatModelApi(
      AnthropicClientFactory clientFactory,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter,
      ModelCapabilities capabilities,
      AnthropicMessageStreamAssembler streamAssembler) {
    this.clientFactory = clientFactory;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.capabilities = capabilities;
    this.streamAssembler = streamAssembler;
  }

  @Override
  public ChatModelResult call(ChatModelRequest request) {
    final MessageCreateParams params =
        requestConverter.toMessageCreateParams(
            request.executionContext(), request.snapshot(), capabilities);

    final long startNanos = System.nanoTime();
    try {
      final AnthropicClient client = clientFactory.create();
      try {
        final Message message;
        try (StreamResponse<RawMessageStreamEvent> stream =
            client.messages().createStreaming(params)) {
          message = streamAssembler.assemble(stream);
        }
        final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
        return responseConverter.toResult(message, executionTime);
      } finally {
        try {
          client.close();
        } catch (Exception closeException) {
          LOG.warn("Failed to close AnthropicClient", closeException);
        }
      }
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
  public ModelCapabilities capabilities() {
    return capabilities;
  }
}
