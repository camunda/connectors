/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.api.ResponseFormat;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * LangChain4j-backed {@link ChatModelApi} used for every wire-protocol family today. Wraps a
 * pre-resolved {@link ChatModel}; one instance per chat invocation, produced by {@link
 * Langchain4JChatModelApiFactory#create}.
 *
 * <p>Public so customers can compose it from their own {@link
 * io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory ChatModelApiFactory}
 * bean — e.g. to wire up a LangChain4j-supported provider we don't ship by passing in their own
 * resolved {@link ChatModel} alongside the framework's converter beans.
 */
public class Langchain4JChatModelApi implements ChatModelApi {

  private final ChatModel chatModel;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;

  public Langchain4JChatModelApi(
      ChatModel chatModel,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    this.chatModel = chatModel;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
  }

  @Override
  public ModelCapabilities capabilities() {
    // Conservative defaults — the bridge is model-agnostic. Native implementations will resolve
    // capabilities from the matrix.
    return new ModelCapabilities(
        List.of(Modality.TEXT),
        List.of(Modality.TEXT),
        List.of(Modality.TEXT),
        false,
        false,
        false,
        true,
        null,
        null);
  }

  @Override
  public CompletableFuture<ChatResponse> complete(
      io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest request,
      ChatOptions options,
      ChatStreamListener listener) {
    try {
      final var l4jMessages = chatMessageConverter.map(request.messages());
      final var toolSpecifications =
          toolSpecificationConverter.asToolSpecifications(request.tools());

      final var l4jRequestBuilder =
          ChatRequest.builder().messages(l4jMessages).toolSpecifications(toolSpecifications);

      final var l4jResponseFormat = toL4jResponseFormat(options.responseFormat());
      if (l4jResponseFormat != null) {
        l4jRequestBuilder.responseFormat(l4jResponseFormat);
      }

      final var l4jResponse = chatModel.chat(l4jRequestBuilder.build());
      final var assistantMessage = chatMessageConverter.toAssistantMessage(l4jResponse);

      return CompletableFuture.completedFuture(
          new ChatResponse(
              assistantMessage, assistantMessage.stopReason(), assistantMessage.usage(), null));
    } catch (Exception e) {
      final var message =
          Optional.ofNullable(e.getMessage())
              .filter(StringUtils::isNotBlank)
              .orElseGet(() -> e.getClass().getSimpleName());
      return CompletableFuture.failedFuture(
          new ConnectorException(
              ERROR_CODE_FAILED_MODEL_CALL, "Model call failed: %s".formatted(message), e));
    }
  }

  private @Nullable dev.langchain4j.model.chat.request.ResponseFormat toL4jResponseFormat(
      @Nullable ResponseFormat responseFormat) {
    if (!(responseFormat instanceof ResponseFormat.Json json)) {
      // Both null and ResponseFormat.Text leave the format unset — matches the previous adapter,
      // which avoided sending TEXT explicitly because some models reject it.
      return null;
    }

    final var builder =
        dev.langchain4j.model.chat.request.ResponseFormat.builder().type(ResponseFormatType.JSON);

    if (json.schema() != null) {
      final var name = StringUtils.isNotBlank(json.schemaName()) ? json.schemaName() : "Response";
      builder.jsonSchema(
          JsonSchema.builder()
              .name(name)
              .rootElement(jsonSchemaConverter.mapToSchema(json.schema()))
              .build());
    }

    return builder.build();
  }
}
