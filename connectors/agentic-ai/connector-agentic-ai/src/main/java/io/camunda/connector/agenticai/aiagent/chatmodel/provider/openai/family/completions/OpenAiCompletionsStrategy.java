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
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiEffort;
import io.camunda.connector.agenticai.aiagent.util.LoggingSupport;
import java.time.Duration;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    final CompletionsRequestSpec spec = toSpec(configuration);
    final ChatCompletionCreateParams params =
        requestConverter.toRequest(
            spec, request.executionContext().configuration().response(), request.snapshot());
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

  /** Maps the OpenAI provider's own configuration onto the provider-neutral request spec. */
  private CompletionsRequestSpec toSpec(OpenAiChatModelConfiguration configuration) {
    final OpenAiConnection connection = configuration.openai();
    final CompletionsParameters params = completionsParameters(connection);
    return new CompletionsRequestSpec(
        connection.model().model(),
        params == null || params.maxCompletionTokens() == null
            ? null
            : params.maxCompletionTokens().longValue(),
        null,
        params == null ? null : params.temperature(),
        params == null ? null : params.topP(),
        effort(params),
        connection.backend().requestCustomizations());
  }

  /**
   * This strategy only handles the {@code completions} API family; routing a {@code responses}
   * family configuration here is a caller/family-dispatch bug, not a user-facing configuration
   * error, hence the unchecked exception rather than a {@code ConnectorException}. {@code
   * completions} itself is optional -- every one of its own fields is optional, so a modeler
   * leaving all of them unset means the object is absent entirely, not present-with-nulls.
   */
  private @Nullable CompletionsParameters completionsParameters(OpenAiConnection connection) {
    return switch (connection.api()) {
      case OpenAiCompletionsApi completionsApi -> completionsApi.completions();
      default ->
          throw new IllegalArgumentException(
              "OpenAiCompletionsStrategy requires the 'completions' API family, but was configured with '%s'"
                  .formatted(connection.api().type()));
    };
  }

  /**
   * Maps the {@code effort} dial to its lowercase wire value, skipping the model-default sentinel.
   */
  private @Nullable String effort(@Nullable CompletionsParameters params) {
    final OpenAiEffort effort = params == null ? null : params.effort();
    if (effort == null || effort == OpenAiEffort.MODEL_DEFAULT) {
      return null;
    }
    return effort.name().toLowerCase(Locale.ROOT);
  }
}
