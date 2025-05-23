/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import io.camunda.connector.agenticai.aiagent.document.CamundaDocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.memory.AgentContextChatMemoryStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.provider.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallResultConverter;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallingHandler;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DefaultAiAgentRequestHandler implements AiAgentRequestHandler {

  private static final int DEFAULT_MAX_MODEL_CALLS = 10;
  private static final int DEFAULT_MAX_MEMORY_MESSAGES = 20;

  private static final String ERROR_CODE_WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS =
      "WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS";
  private static final String ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT =
      "TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT";
  private static final String ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED =
      "MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED";

  private final ObjectMapper objectMapper;
  private final ChatModelFactory chatModelFactory;
  private final ToolCallingHandler toolCallingHandler;
  private final ToolCallResultConverter toolCallResultConverter;
  private final CamundaDocumentToContentConverter documentConverter;

  public DefaultAiAgentRequestHandler(
      ObjectMapper objectMapper,
      ChatModelFactory chatModelFactory,
      ToolCallingHandler toolCallingHandler,
      ToolCallResultConverter toolCallResultConverter,
      CamundaDocumentToContentConverter documentConverter) {
    this.objectMapper = objectMapper;
    this.chatModelFactory = chatModelFactory;
    this.toolCallingHandler = toolCallingHandler;
    this.toolCallResultConverter = toolCallResultConverter;
    this.documentConverter = documentConverter;
  }

  @Override
  public AgentResponse handleRequest(OutboundConnectorContext context, AgentRequest request) {
    final AgentRequest.AgentRequestData requestData = request.data();
    final AgentContext agentContext =
        Optional.ofNullable(requestData.context()).orElseGet(AgentContext::empty);

    // initialize configured model
    final ChatModel chatModel = chatModelFactory.createChatModel(request.provider());

    // set up memory and load from context if available
    final AgentContextChatMemoryStore chatMemoryStore =
        new AgentContextChatMemoryStore(objectMapper);
    chatMemoryStore.loadFromAgentContext(agentContext);

    final ChatMemory chatMemory =
        MessageWindowChatMemory.builder()
            .maxMessages(
                Optional.ofNullable(requestData.memory())
                    .map(MemoryConfiguration::maxMessages)
                    .orElse(DEFAULT_MAX_MEMORY_MESSAGES))
            .chatMemoryStore(chatMemoryStore)
            .build();

    // check configured limits
    checkLimits(requestData, agentContext);

    // update memory with system + new user messages/tool call responses
    addSystemPromptIfNecessary(chatMemory, requestData);
    addUserMessagesFromRequest(agentContext, chatMemory, requestData);

    // call LLM API with updated messages + resolved tool specifications
    final var toolSpecifications = loadToolSpecifications(context, requestData);
    final var chatRequest =
        ChatRequest.builder()
            .messages(chatMemory.messages())
            .toolSpecifications(toolSpecifications)
            .build();

    final ChatResponse chatResponse = chatModel.chat(chatRequest);
    final AiMessage aiMessage = chatResponse.aiMessage();
    chatMemory.add(aiMessage);

    // extract tool call requests from LLM response
    final var toolCalls = toolCallingHandler.extractToolCalls(toolSpecifications, aiMessage);
    final var nextAgentState =
        !toolCalls.isEmpty() ? AgentState.WAITING_FOR_TOOL_INPUT : AgentState.READY;

    // update context
    final var updatedContext =
        chatMemoryStore
            .storeToAgentContext(agentContext)
            .withState(nextAgentState)
            .withMetrics(
                agentContext
                    .metrics()
                    .incrementModelCalls(1)
                    .incrementTokenUsage(AgentMetrics.TokenUsage.from(chatResponse.tokenUsage())));

    return new AgentResponse(updatedContext, updatedContext.memory().getLast(), toolCalls);
  }

  private void checkLimits(AgentRequest.AgentRequestData requestData, AgentContext agentContext) {
    final int maxModelCalls =
        Optional.ofNullable(requestData.limits())
            .map(LimitsConfiguration::maxModelCalls)
            .orElse(DEFAULT_MAX_MODEL_CALLS);
    if (agentContext.metrics().modelCalls() >= maxModelCalls) {
      throw new ConnectorException(
          ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED,
          "Maximum number of model calls reached (modelCalls: %d, limit: %d)"
              .formatted(agentContext.metrics().modelCalls(), maxModelCalls));
    }
  }

  private void addSystemPromptIfNecessary(
      ChatMemory chatMemory, AgentRequest.AgentRequestData requestData) {
    // add system prompt if this is the first request
    if (chatMemory.messages().isEmpty()) {
      chatMemory.add(promptFromConfiguration(requestData.systemPrompt()).toSystemMessage());
    }
  }

  private void addUserMessagesFromRequest(
      AgentContext agentContext, ChatMemory chatMemory, AgentRequest.AgentRequestData requestData) {
    final var toolCallResults =
        Optional.ofNullable(requestData.tools())
            .map(ToolsConfiguration::toolCallResults)
            .filter(tcr -> !tcr.isEmpty())
            .orElseGet(Collections::emptyList);

    // throw an error when receiving tool call results on an empty context as
    // most likely this is a modeling error
    if (agentContext.isEmpty() && !toolCallResults.isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT,
          "Agent received tool call results, but the agent context was empty (no tool call requests). Is the context configured correctly?");
    }

    // add tool call results to chat memory
    if (agentContext.isInState(AgentState.WAITING_FOR_TOOL_INPUT)) {
      if (toolCallResults.isEmpty()) {
        throw new ConnectorException(
            ERROR_CODE_WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS,
            "Agent is waiting for tool input, but tool call results were empty. Is the tool feedback loop configured correctly?");
      }

      toolCallResults.stream()
          .map(toolCallResultConverter::asToolExecutionResultMessage)
          .forEach(chatMemory::add);
    } else {
      // feed messages with the user input message (first iteration or user follow-up request)
      final var userPrompt = requestData.userPrompt();
      final var userMessageBuilder =
          UserMessage.builder()
              .addContent(new TextContent(promptFromConfiguration(userPrompt).text()));

      // add documents as content blocks
      Optional.ofNullable(userPrompt.documents())
          .orElseGet(Collections::emptyList)
          .forEach(document -> userMessageBuilder.addContent(documentConverter.convert(document)));

      chatMemory.add(userMessageBuilder.build());
    }
  }

  private Prompt promptFromConfiguration(PromptConfiguration promptConfiguration) {
    final var parameters =
        Optional.ofNullable(promptConfiguration.parameters()).orElseGet(Collections::emptyMap);
    return PromptTemplate.from(promptConfiguration.prompt()).apply(parameters);
  }

  private List<ToolSpecification> loadToolSpecifications(
      OutboundConnectorContext context, AgentRequest.AgentRequestData requestData) {
    return Optional.ofNullable(requestData.tools())
        .map(ToolsConfiguration::containerElementId)
        .filter(id -> !id.isBlank())
        .map(
            containerElementId ->
                toolCallingHandler.loadToolSpecifications(
                    context.getJobContext().getProcessDefinitionKey(), containerElementId))
        .orElseGet(Collections::emptyList);
  }
}
