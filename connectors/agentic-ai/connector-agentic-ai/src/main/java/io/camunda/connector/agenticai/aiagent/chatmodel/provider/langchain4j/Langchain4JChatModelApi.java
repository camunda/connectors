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
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ChatModelApi} routing a chat request through a LangChain4J {@code
 * dev.langchain4j.model.chat.ChatModel}: converts the domain conversation and tool definitions to
 * LangChain4J types, drives one {@code chat()} call, times it, and converts the response back.
 */
public class Langchain4JChatModelApi implements ChatModelApi {

  private static final Logger LOG = LoggerFactory.getLogger(Langchain4JChatModelApi.class);

  /**
   * Uniform conservative capability profile for every provider routed through LangChain4J.
   * Deliberately NOT resolved via {@code ModelCapabilitiesResolver} — that resolver's own
   * conservative default is {@code [TEXT]} for user messages, which would regress today's
   * document-in-user-message support ({@code DocumentToContentConverterImpl} already accepts text,
   * image and PDF content). This constant is distinct from that resolver default; do not conflate
   * them.
   */
  public static final ModelCapabilities DEFAULT_CAPABILITIES =
      new CoreModelCapabilities(
          List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT),
          List.of(),
          List.of(Modality.TEXT),
          null,
          null);

  private final CloseableChatModel chatModel;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;
  private final ModelCapabilities capabilities;
  private final Function<@Nullable TokenUsage, AgentMetrics.TokenUsage> tokenUsageMapper;

  public Langchain4JChatModelApi(
      CloseableChatModel chatModel,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ModelCapabilities capabilities,
      Function<@Nullable TokenUsage, AgentMetrics.TokenUsage> tokenUsageMapper) {
    this.chatModel = chatModel;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
    this.capabilities = capabilities;
    this.tokenUsageMapper = tokenUsageMapper;
  }

  @Override
  public ChatModelResult call(ChatModelRequest request) {
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
    return new ChatModelResult.Completed(assistantMessage, metrics);
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
  public ModelCapabilities capabilities() {
    return capabilities;
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
