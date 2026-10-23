/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.CompletionsRequestSpec;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import java.util.Locale;
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

  private final OpenAIClient client;
  private final MistralConfiguration configuration;
  private final OpenAiCompletionsRequestConverter requestConverter;
  private final OpenAiCompletionsResponseConverter responseConverter;
  private final OpenAiCompletionsStreamAssembler streamAssembler;

  public MistralChatModel(
      OpenAIClient client,
      MistralConfiguration configuration,
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
    final CompletionsRequestSpec spec = toSpec(configuration);
    final ChatCompletionCreateParams params =
        requestConverter.toRequest(
            spec, request.executionContext().configuration().response(), request.snapshot());

    final ChatCompletion completion;
    try (StreamResponse<ChatCompletionChunk> stream =
        client.chat().completions().createStreaming(params)) {
      completion = streamAssembler.assemble(stream);
    }
    return responseConverter.toResult(completion);
  }

  /** Maps this provider's own configuration onto the shared Chat Completions request spec. */
  private CompletionsRequestSpec toSpec(MistralConfiguration configuration) {
    return new CompletionsRequestSpec(
        configuration.model(),
        null,
        configuration.maxTokens() == null ? null : configuration.maxTokens().longValue(),
        configuration.temperature(),
        configuration.topP(),
        configuration.effort() == null
            ? null
            : configuration.effort().name().toLowerCase(Locale.ROOT),
        configuration.backend().customizations());
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
