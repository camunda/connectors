/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.errors.BadRequestException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.CompletionsRequestSpec;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralParameters;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mistral {@link ChatModel}: reuses the OpenAI provider's Chat Completions wire mapper wholesale
 * (Mistral's own API is OpenAI-Completions-shaped), mapping this provider's own configuration onto
 * the mapper's provider-neutral {@link CompletionsRequestSpec} rather than routing through {@code
 * OpenAiCompletionsStrategy}, which is typed to the OpenAI provider's own configuration.
 *
 * <p>The {@link OpenAIClient} is built once by the factory and owned for the lifetime of this
 * instance (one agent request, across all continuation rounds); {@link #close()} closes it once.
 */
public class MistralChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(MistralChatModel.class);

  // Mistral's error codes are plain strings on the wire, mirrored the same way OpenAI's are (see
  // OpenAiChatModel) -- this is the only one requiring dedicated handling.
  private static final String MISTRAL_ERROR_CODE_CONTEXT_LENGTH_EXCEEDED =
      "context_length_exceeded";

  private static final String GENERIC_SDK_FAILURE_MESSAGE = "Request failed";

  private final OpenAIClient client;
  private final MistralChatModelConfiguration configuration;
  private final OpenAiCompletionsRequestConverter requestConverter;
  private final OpenAiCompletionsResponseConverter responseConverter;
  private final OpenAiCompletionsStreamAssembler streamAssembler;

  public MistralChatModel(
      OpenAIClient client,
      MistralChatModelConfiguration configuration,
      OpenAiCompletionsRequestConverter requestConverter,
      OpenAiCompletionsResponseConverter responseConverter,
      OpenAiCompletionsStreamAssembler streamAssembler) {
    this.client = client;
    this.configuration = configuration;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    try {
      final CompletionsRequestSpec spec = toSpec(configuration);
      final ChatCompletionCreateParams params =
          requestConverter.toRequest(
              spec, request.executionContext().configuration().response(), request.snapshot());

      final long startNanos = System.nanoTime();
      final ChatCompletion completion;
      try (StreamResponse<ChatCompletionChunk> stream =
          client.chat().completions().createStreaming(params)) {
        completion = streamAssembler.assemble(stream);
      }
      final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
      return responseConverter.toResult(completion, executionTime);
    } catch (ConnectorException | ChatModelRejectedException e) {
      throw e;
    } catch (BadRequestException e) {
      if (MISTRAL_ERROR_CODE_CONTEXT_LENGTH_EXCEEDED.equals(e.code().orElse(null))) {
        throw new ContextWindowExceededException(failureMessage(e), e, null);
      }
      throw new ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, failureMessage(e), e);
    } catch (Exception e) {
      throw new ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, failureMessage(e), e);
    }
  }

  /** Maps this provider's own configuration onto the shared Chat Completions request spec. */
  private CompletionsRequestSpec toSpec(MistralChatModelConfiguration configuration) {
    final MistralConnection connection = configuration.mistral();
    final MistralParameters params = connection.parameters();
    return new CompletionsRequestSpec(
        connection.model().model(),
        null,
        params == null || params.maxTokens() == null ? null : params.maxTokens().longValue(),
        params == null ? null : params.temperature(),
        params == null ? null : params.topP(),
        effort(params),
        connection.backend().requestCustomizations());
  }

  /**
   * Maps the {@code effort} dial to its lowercase wire value, skipping the model-default sentinel.
   */
  private @Nullable String effort(@Nullable MistralParameters params) {
    final MistralEffort effort = params == null ? null : params.effort();
    if (effort == null || effort == MistralEffort.MODEL_DEFAULT) {
      return null;
    }
    return effort.name().toLowerCase(Locale.ROOT);
  }

  private static String failureMessage(Exception e) {
    final String outerMessage = e.getMessage();
    final boolean preferCause =
        outerMessage == null
            || outerMessage.isBlank()
            || GENERIC_SDK_FAILURE_MESSAGE.equals(outerMessage);
    if (!preferCause) {
      return "Model call failed: %s".formatted(outerMessage);
    }
    final Throwable cause = e.getCause();
    final String detail =
        cause != null
            ? Optional.ofNullable(cause.getMessage())
                .filter(m -> !m.isBlank())
                .orElseGet(() -> cause.getClass().getSimpleName())
            : outerMessage != null && !outerMessage.isBlank()
                ? outerMessage
                : e.getClass().getSimpleName();
    return "Model call failed: %s".formatted(detail);
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.error("Failed to close OpenAIClient", e);
    }
  }
}
