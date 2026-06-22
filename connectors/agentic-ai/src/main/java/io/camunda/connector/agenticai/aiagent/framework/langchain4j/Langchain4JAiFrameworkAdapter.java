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
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JAiFrameworkAdapter
    implements AiFrameworkAdapter<Langchain4JAiFrameworkChatResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(Langchain4JAiFrameworkAdapter.class);

  private final ChatModelFactory chatModelFactory;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;

  public Langchain4JAiFrameworkAdapter(
      ChatModelFactory chatModelFactory,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    this.chatModelFactory = chatModelFactory;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
  }

  @Override
  public Langchain4JAiFrameworkChatResponse executeChatRequest(
      AgentExecutionContext executionContext, ConversationSnapshot snapshot) {
    final var messages = chatMessageConverter.map(snapshot.messages());
    final var toolSpecifications =
        toolSpecificationConverter.asToolSpecifications(snapshot.toolDefinitions());

    final var configuration = executionContext.configuration();
    final var chatRequestBuilder =
        ChatRequest.builder().messages(messages).toolSpecifications(toolSpecifications);
    configureResponseFormat(chatRequestBuilder, configuration.response());

    try (final var chatModel = chatModelFactory.createChatModel(configuration.provider())) {
      final ChatResponse chatResponse = doChat(chatModel, chatRequestBuilder);
      final AssistantMessage assistantMessage =
          chatMessageConverter.toAssistantMessage(chatResponse);

      final var metrics = buildMetrics(chatResponse, assistantMessage);
      return new Langchain4JAiFrameworkChatResponse(assistantMessage, metrics, chatResponse);
    }
  }

  private AgentMetrics buildMetrics(ChatResponse chatResponse, AssistantMessage assistantMessage) {
    return AgentMetrics.builder()
        .modelCalls(1)
        .tokenUsage(tokenUsage(chatResponse.tokenUsage()))
        .toolCalls(assistantMessage.toolCalls() == null ? 0 : assistantMessage.toolCalls().size())
        .build();
  }

  private void configureResponseFormat(
      ChatRequest.Builder chatRequestBuilder, ResponseConfiguration responseConfiguration) {
    final var responseFormat = createResponseFormat(responseConfiguration);
    if (responseFormat != null) {
      chatRequestBuilder.responseFormat(responseFormat);
    }
  }

  private ResponseFormat createResponseFormat(ResponseConfiguration responseConfiguration) {
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

  private ChatResponse doChat(ChatModel chatModel, ChatRequest.Builder chatRequestBuilder) {
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

  private AgentMetrics.TokenUsage tokenUsage(TokenUsage tokenUsage) {
    if (tokenUsage == null) {
      return AgentMetrics.TokenUsage.empty();
    }

    return AgentMetrics.TokenUsage.builder()
        .inputTokenCount(Optional.ofNullable(tokenUsage.inputTokenCount()).orElse(0))
        .outputTokenCount(Optional.ofNullable(tokenUsage.outputTokenCount()).orElse(0))
        .build();
  }
}
