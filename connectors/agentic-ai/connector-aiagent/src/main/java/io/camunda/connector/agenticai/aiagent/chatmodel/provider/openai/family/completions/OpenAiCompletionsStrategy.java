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
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.util.LoggingSupport;
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
      OpenAIClient client, OpenAiConfiguration configuration, ChatRequest request) {
    final CompletionsRequestSpec spec = toSpec(configuration);
    final ChatCompletionCreateParams params =
        requestConverter.toRequest(
            spec, request.executionContext().configuration().response(), request.snapshot());
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "OpenAI Chat Completions API request: {}", LoggingSupport.toJson(MAPPER, params._body()));
    }

    final ChatCompletion completion;
    try (StreamResponse<ChatCompletionChunk> stream =
        client.chat().completions().createStreaming(params)) {
      completion = streamAssembler.assemble(stream);
    }
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "OpenAI Chat Completions API response: {}", LoggingSupport.toJson(MAPPER, completion));
    }
    return responseConverter.toResult(completion);
  }

  /**
   * Maps the OpenAI provider's own configuration onto the provider-neutral request spec. This
   * strategy only handles the {@code completions} API family; routing a {@code responses} family
   * configuration here is a caller/family-dispatch bug, not a user-facing configuration error,
   * hence the unchecked exception rather than a {@code ConnectorException}.
   */
  private CompletionsRequestSpec toSpec(OpenAiConfiguration configuration) {
    if (!(configuration.api() instanceof OpenAiConfiguration.Api.Completions completions)) {
      throw new IllegalArgumentException(
          "OpenAiCompletionsStrategy requires the 'completions' API family, but was configured with '%s'"
              .formatted(configuration.api().type()));
    }
    return new CompletionsRequestSpec(
        configuration.model(),
        completions.maxCompletionTokens() == null
            ? null
            : completions.maxCompletionTokens().longValue(),
        null,
        completions.temperature(),
        completions.topP(),
        effort(completions.effort()),
        configuration.backend().customizations());
  }

  /** Maps the {@code effort} dial to its lowercase wire value. */
  private @Nullable String effort(OpenAiConfiguration.@Nullable Effort effort) {
    return effort == null ? null : effort.name().toLowerCase(Locale.ROOT);
  }
}
