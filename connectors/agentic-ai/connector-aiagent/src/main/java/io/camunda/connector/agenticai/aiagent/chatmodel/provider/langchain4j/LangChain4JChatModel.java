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
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelRejectedException.PartialResult;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetricsTokenUsageBuilder;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason;
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
 * {@link ChatModel} routing a chat request through a LangChain4j {@code
 * dev.langchain4j.model.chat.ChatModel}: converts the domain conversation and tool definitions to
 * LangChain4j types, drives one {@code chat()} call, times it, and converts the response back.
 *
 * <p>The LangChain4j framework does not support pause/continuation semantics, so {@link
 * #execute(io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest)} always returns a
 * {@link ChatResult.Completed} — the continuation loop in {@code BaseAgentRequestHandler} therefore
 * runs exactly once on this path, matching the single-shot call.
 */
public class LangChain4JChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(LangChain4JChatModel.class);

  private final CloseableChatModel chatModel;
  private final String provider;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;
  private final ToolCallMetadataCodec toolCallMetadataCodec;
  private final Function<@Nullable TokenUsage, AgentMetrics.TokenUsage> tokenUsageMapper;

  /**
   * Creates a model that carries no provider data through the tool calls ({@link
   * ToolCallMetadataCodec#NONE}) and maps the base token usage ({@link #mapTokenUsage}).
   *
   * @param chatModel the LangChain4j model. The connector closes it with this model.
   * @param configuration the chat model configuration. The model keeps only its provider ID, which
   *     is the namespace of the data that the tool calls carry.
   */
  public LangChain4JChatModel(
      CloseableChatModel chatModel,
      ChatModelConfiguration configuration,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    this(
        chatModel,
        configuration,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        ToolCallMetadataCodec.NONE,
        LangChain4JChatModel::mapTokenUsage);
  }

  /**
   * @param chatModel the LangChain4j model. The connector closes it with this model.
   * @param configuration the chat model configuration. The model keeps only its provider ID, which
   *     is the namespace of the data that the tool calls carry.
   * @param toolCallMetadataCodec carries provider data through the tool calls
   * @param tokenUsageMapper maps the token usage of a response. A mapper that adds the cache or
   *     reasoning token detail of its provider starts from {@link #baseTokenUsageBuilder}.
   */
  public LangChain4JChatModel(
      CloseableChatModel chatModel,
      ChatModelConfiguration configuration,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ToolCallMetadataCodec toolCallMetadataCodec,
      Function<@Nullable TokenUsage, AgentMetrics.TokenUsage> tokenUsageMapper) {
    this.chatModel = chatModel;
    this.provider = configuration.provider();
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
    this.toolCallMetadataCodec = toolCallMetadataCodec;
    this.tokenUsageMapper = tokenUsageMapper;
  }

  /**
   * Maps the input and output token counts of a response. This is the default of the constructor
   * without a mapper. A custom mapper that adds the cache or reasoning token detail of its provider
   * starts from {@link #baseTokenUsageBuilder}.
   */
  public static AgentMetrics.TokenUsage mapTokenUsage(@Nullable TokenUsage usage) {
    if (usage == null) {
      return AgentMetrics.TokenUsage.empty();
    }

    return baseTokenUsageBuilder(usage).build();
  }

  /** The input and output token counts of a response, for a custom mapper to extend. */
  public static AgentMetricsTokenUsageBuilder baseTokenUsageBuilder(TokenUsage usage) {
    return AgentMetrics.TokenUsage.builder()
        .inputTokenCount(usage.inputTokenCount() != null ? usage.inputTokenCount() : 0)
        .outputTokenCount(usage.outputTokenCount() != null ? usage.outputTokenCount() : 0);
  }

  @Override
  public ChatResult execute(
      io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest request) {
    final var executionContext = request.executionContext();
    final var snapshot = request.snapshot();

    final var messages =
        chatMessageConverter.map(snapshot.messages(), provider, toolCallMetadataCodec);
    final var toolSpecifications =
        toolSpecificationConverter.asToolSpecifications(snapshot.toolDefinitions());

    final var configuration = executionContext.configuration();
    final var chatRequestBuilder =
        ChatRequest.builder().messages(messages).toolSpecifications(toolSpecifications);
    configureResponseFormat(chatRequestBuilder, configuration.response());

    final long startNanos = System.nanoTime();
    final ChatResponse chatResponse = doChat(chatRequestBuilder);
    final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);

    final AssistantMessage assistantMessage =
        chatMessageConverter.toAssistantMessage(chatResponse, provider, toolCallMetadataCodec);
    final var metrics = buildMetrics(chatResponse, assistantMessage, executionTime);

    if (assistantMessage.stopReason() instanceof UnknownStopReason unknown
        && FinishReason.CONTENT_FILTER.name().equals(unknown.value())) {
      throw new ContentFilteredException(
          "Model response was blocked by provider content filtering.",
          new PartialResult(assistantMessage, metrics));
    }

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
      LOG.error("Failed to close CloseableChatModel", e);
    }
  }
}
