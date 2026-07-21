/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ChatModel} routing a chat request through a LangChain4J {@code
 * dev.langchain4j.model.chat.ChatModel}: converts the domain conversation and tool definitions to
 * LangChain4J types, drives one {@code chat()} call, times it, and converts the response back.
 *
 * <p>The LangChain4J framework does not support pause/continuation semantics, so {@link
 * #execute(io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest)} always returns a {@link
 * ChatResult.Completed} — the continuation loop in {@code BaseAgentRequestHandler} therefore runs
 * exactly once on this path, matching the single-shot call.
 */
public class LangChain4JChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(LangChain4JChatModel.class);

  private final CloseableChatModel chatModel;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;
  private final Function<@Nullable TokenUsage, AgentMetrics.TokenUsage> tokenUsageMapper;

  public LangChain4JChatModel(
      CloseableChatModel chatModel,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      Function<@Nullable TokenUsage, AgentMetrics.TokenUsage> tokenUsageMapper) {
    this.chatModel = chatModel;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
    this.tokenUsageMapper = tokenUsageMapper;
  }

  @Override
  public ChatResult execute(io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest request) {
    final var executionContext = request.executionContext();
    final var snapshot = request.snapshot();

    final var messages = chatMessageConverter.map(snapshot.messages());
    final var toolSpecifications =
        toolSpecificationConverter.asToolSpecifications(snapshot.toolDefinitions());

    final var configuration = executionContext.configuration();
    final var chatRequestBuilder =
        ChatRequest.builder().messages(messages).toolSpecifications(toolSpecifications);
    configureResponseFormat(chatRequestBuilder, configuration.response());

    final long startNanos = System.nanoTime();
    final ChatResponse chatResponse = doChat(chatRequestBuilder);
    final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);

    final AssistantMessage assistantMessage = chatMessageConverter.toAssistantMessage(chatResponse);
    final var metrics = buildMetrics(chatResponse, assistantMessage, executionTime);
    return new ChatResult.Completed(assistantMessage, metrics);
  }

  private AgentMetrics buildMetrics(
      ChatResponse chatResponse, AssistantMessage assistantMessage, Duration executionTime) {
    return AgentMetrics.builder()
        .modelCalls(1)
        .tokenUsage(tokenUsageMapper.apply(chatResponse.tokenUsage()))
        .toolCalls(assistantMessage.toolCalls() == null ? 0 : assistantMessage.toolCalls().size())
        .executionTime(executionTime)
        .build();
  }

  private void configureResponseFormat(
      ChatRequest.Builder chatRequestBuilder,
      @Nullable ResponseConfiguration responseConfiguration) {
    final var responseFormat = createResponseFormat(responseConfiguration);
    if (responseFormat != null) {
      chatRequestBuilder.responseFormat(responseFormat);
    }
  }

  private @Nullable ResponseFormat createResponseFormat(
      @Nullable ResponseConfiguration responseConfiguration) {
    // do not explicitely configure response format to TEXT as (depending on the model) this might
    // lead to exceptions
    if (responseConfiguration != null
        && responseConfiguration.format() != null
        && responseConfiguration.format() instanceof JsonResponseFormatConfiguration jsonFormat) {
      final var builder = ResponseFormat.builder().type(ResponseFormatType.JSON);
      if (jsonFormat.schema() != null) {
        final var jsonSchema =
            JsonSchema.builder()
                .name(
                    Optional.ofNullable(jsonFormat.schemaName())
                        .filter(StringUtils::isNotBlank)
                        .orElse("Response"))
                .rootElement(jsonSchemaConverter.mapToSchema(jsonFormat.schema()))
                .build();
        builder.jsonSchema(jsonSchema);
      }

      return builder.build();
    }

    return null;
  }

  private ChatResponse doChat(ChatRequest.Builder chatRequestBuilder) {
    try {
      return chatModel.chat(chatRequestBuilder.build());
    } catch (Exception e) {
      final var message =
          Optional.ofNullable(e.getMessage())
              .filter(StringUtils::isNotBlank)
              .orElseGet(() -> e.getClass().getSimpleName());

      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "Model call failed: %s".formatted(message), e);
    }
  }

  @Override
  public void close() {
    try {
      chatModel.close();
    } catch (Exception e) {
      LOG.warn("Failed to close CloseableChatModel", e);
    }
  }
}
