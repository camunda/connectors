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
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.MessageCreateParams;
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
 * Native Anthropic {@link ChatModelApi}: drives the vendor SDK's streaming beta Messages endpoint
 * for every call (Anthropic has no meaningful non-streaming distinction for this connector's
 * purposes; streaming is used uniformly to accumulate the same {@link BetaMessage} shape the
 * non-streaming API would return), then delegates to the Task 3/4 converters to translate to/from
 * the domain model.
 *
 * <p>{@link AnthropicClient#close()} is deliberately not exercised through try-with-resources: the
 * vendor SDK's {@code AnthropicClient} interface does not implement {@link AutoCloseable} (its
 * {@code close()} is a plain, unchecked method the SDK explicitly documents as usually unnecessary
 * to call). {@link StreamResponse}, in contrast, does implement {@code AutoCloseable} and is closed
 * via try-with-resources.
 *
 * <p>Uses the <strong>beta</strong> messages client (rather than the stable {@code
 * client.messages()}) since it is required for upcoming Skills support; this migration is otherwise
 * behavior-identical.
 */
public class AnthropicChatModelApi implements ChatModelApi {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicChatModelApi.class);

  private final AnthropicClientFactory clientFactory;
  private final AnthropicMessageRequestConverter requestConverter;
  private final AnthropicMessageResponseConverter responseConverter;
  private final AnthropicModelCapabilities capabilities;

  /**
   * Whether the configured model matched a capability matrix entry (see {@link
   * io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver#matches}),
   * as opposed to falling through to family/conservative defaults. Threaded into the request
   * converter so {@link AnthropicReasoningValidator} can distinguish "declared but not
   * reasoning-capable" (validate) from "unknown/custom model" (pass-through).
   */
  private final boolean modelMatched;

  private final AnthropicMessageStreamAssembler streamAssembler;

  public AnthropicChatModelApi(
      AnthropicClientFactory clientFactory,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter,
      AnthropicModelCapabilities capabilities,
      boolean modelMatched) {
    this(
        clientFactory,
        requestConverter,
        responseConverter,
        capabilities,
        modelMatched,
        AnthropicMessageStreamAssembler.accumulating());
  }

  AnthropicChatModelApi(
      AnthropicClientFactory clientFactory,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter,
      AnthropicModelCapabilities capabilities,
      boolean modelMatched,
      AnthropicMessageStreamAssembler streamAssembler) {
    this.clientFactory = clientFactory;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.capabilities = capabilities;
    this.modelMatched = modelMatched;
    this.streamAssembler = streamAssembler;
  }

  @Override
  public ChatModelResult call(ChatModelRequest request) {
    final MessageCreateParams params =
        requestConverter.toMessageCreateParams(
            request.executionContext(), request.snapshot(), capabilities, modelMatched);

    final long startNanos = System.nanoTime();
    try {
      final AnthropicClient client = clientFactory.create();
      try {
        final BetaMessage message;
        try (StreamResponse<BetaRawMessageStreamEvent> stream =
            client.beta().messages().createStreaming(params)) {
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
