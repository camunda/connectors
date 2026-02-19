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
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class Langchain4JAiFrameworkAdapter
    implements AiFrameworkAdapter<Langchain4JAiFrameworkChatResponse> {

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
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory runtimeMemory) {
    final var messages = chatMessageConverter.map(runtimeMemory.filteredMessages());
    final var toolSpecifications =
        toolSpecificationConverter.asToolSpecifications(agentContext.toolDefinitions());

    final var chatRequestBuilder =
        ChatRequest.builder().messages(messages).toolSpecifications(toolSpecifications);
    configureResponseFormat(chatRequestBuilder, executionContext.response());

    final ChatModel chatModel = chatModelFactory.createChatModel(executionContext.provider());
    final ChatResponse chatResponse = doChat(chatModel, chatRequestBuilder);
    final AssistantMessage assistantMessage = chatMessageConverter.toAssistantMessage(chatResponse);

    final var updatedAgentContext =
        agentContext.withMetrics(
            agentContext
                .metrics()
                .incrementModelCalls(1)
                .incrementTokenUsage(tokenUsage(chatResponse.tokenUsage())));

    return new Langchain4JAiFrameworkChatResponse(
        updatedAgentContext, assistantMessage, chatResponse);
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
