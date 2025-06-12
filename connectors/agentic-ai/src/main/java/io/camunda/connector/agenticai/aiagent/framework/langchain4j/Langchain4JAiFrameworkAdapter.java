/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import java.util.Optional;

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
      AgentRequest request, AgentContext agentContext, RuntimeMemory runtimeMemory) {

    final var messages = chatMessageConverter.map(runtimeMemory.filteredMessages());
    final var toolSpecifications =
        toolSpecificationConverter.asToolSpecifications(agentContext.toolDefinitions());
    final var responseFormat = createResponseFormat(request);

    final var chatRequest =
        ChatRequest.builder()
            .messages(messages)
            .toolSpecifications(toolSpecifications)
            .responseFormat(responseFormat)
            .build();

    final ChatModel chatModel = chatModelFactory.createChatModel(request.provider());
    final ChatResponse chatResponse = chatModel.chat(chatRequest);
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

  private ResponseFormat createResponseFormat(AgentRequest request) {
    final var builder = ResponseFormat.builder();

    final var responseConfiguration = request.data().response();
    if (responseConfiguration != null
        && responseConfiguration.format() != null
        && responseConfiguration.format() instanceof JsonResponseFormatConfiguration jsonFormat) {
      builder.type(ResponseFormatType.JSON);

      if (jsonFormat.schema() != null) {
        final var jsonSchema =
            JsonSchema.builder()
                .name(jsonFormat.schemaName())
                .rootElement(jsonSchemaConverter.mapToSchema(jsonFormat.schema()))
                .build();
        builder.jsonSchema(jsonSchema);
      }
    } else {
      builder.type(ResponseFormatType.TEXT);
    }

    return builder.build();
  }

  private AgentMetrics.TokenUsage tokenUsage(dev.langchain4j.model.output.TokenUsage tokenUsage) {
    if (tokenUsage == null) {
      return AgentMetrics.TokenUsage.empty();
    }

    return AgentMetrics.TokenUsage.builder()
        .inputTokenCount(Optional.ofNullable(tokenUsage.inputTokenCount()).orElse(0))
        .outputTokenCount(Optional.ofNullable(tokenUsage.outputTokenCount()).orElse(0))
        .build();
  }
}
