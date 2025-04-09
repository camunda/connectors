/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.AgentContextChatMemoryStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.provider.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallingHandler;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.Optional;

public class DefaultAiAgentRequestHandler implements AiAgentRequestHandler {

  private static final int DEFAULT_MAX_MODEL_CALLS = 10;
  private static final int DEFAULT_MAX_HISTORY_MESSAGES = 20;

  private final ObjectMapper objectMapper;
  private final ChatModelFactory chatModelFactory;
  private final ToolCallingHandler toolCallingHandler;

  public DefaultAiAgentRequestHandler(
      ObjectMapper objectMapper,
      ChatModelFactory chatModelFactory,
      ToolCallingHandler toolCallingHandler) {
    this.objectMapper = objectMapper;
    this.chatModelFactory = chatModelFactory;
    this.toolCallingHandler = toolCallingHandler;
  }

  @Override
  public AgentResponse handleRequest(OutboundConnectorContext context, AgentRequest request) {
    final AgentRequest.AgentRequestData requestData = request.data();
    final AgentContext agentContext =
        Optional.ofNullable(requestData.context()).orElseGet(AgentContext::empty);

    // initialize configured model
    final ChatLanguageModel chatModel = chatModelFactory.createChatModel(request);

    // set up memory and load from context if available
    final AgentContextChatMemoryStore chatMemoryStore =
        new AgentContextChatMemoryStore(objectMapper);
    chatMemoryStore.loadFromAgentContext(agentContext);

    final ChatMemory chatMemory =
        MessageWindowChatMemory.builder()
            .maxMessages(
                Optional.ofNullable(requestData.history().maxMessages())
                    .orElse(DEFAULT_MAX_HISTORY_MESSAGES))
            .chatMemoryStore(chatMemoryStore)
            .build();

    // check configured guardrails
    checkGuardrails(requestData, agentContext);

    // update history with system + new user messages/tool call responses
    addSystemPromptIfNecessary(chatMemory, requestData);
    addUserMessagesFromRequest(agentContext, chatMemory, requestData);

    // call LLM API with updates messages + resolved tool specifications
    final var toolSpecifications =
        toolCallingHandler.loadToolSpecifications(
            context.getJobContext().getProcessDefinitionKey(),
            requestData.tools().containerElementId());

    final var chatRequest =
        ChatRequest.builder()
            .messages(chatMemory.messages())
            .toolSpecifications(toolSpecifications)
            .build();

    final ChatResponse chatResponse = chatModel.chat(chatRequest);
    final AiMessage aiMessage = chatResponse.aiMessage();
    chatMemory.add(aiMessage);

    // extract tool call requests from LLM response
    final var toolsToCall = toolCallingHandler.extractToolsToCall(toolSpecifications, aiMessage);
    final var nextAgentState =
        !toolsToCall.isEmpty() ? AgentState.WAITING_FOR_TOOL_INPUT : AgentState.READY;

    // update context
    final var updatedContext =
        chatMemoryStore
            .storeToAgentContext(agentContext)
            .withState(nextAgentState)
            .withMetrics(
                agentContext
                    .metrics()
                    .incrementModelCalls(1)
                    .incrementTokenUsage(chatResponse.tokenUsage().totalTokenCount()));

    return new AgentResponse(updatedContext, updatedContext.history().getLast(), toolsToCall);
  }

  private void checkGuardrails(
      AgentRequest.AgentRequestData requestData, AgentContext agentContext) {
    // naive guardrail implementation
    final int maxModelCalls =
        Optional.ofNullable(requestData.guardrails().maxModelCalls())
            .orElse(DEFAULT_MAX_MODEL_CALLS);
    if (agentContext.metrics().modelCalls() >= maxModelCalls) {
      throw new ConnectorException("Maximum number of model calls reached");
    }
  }

  private void addSystemPromptIfNecessary(
      ChatMemory chatMemory, AgentRequest.AgentRequestData requestData) {
    // add system prompt if this is the first request
    if (chatMemory.messages().isEmpty()) {
      chatMemory.add(SystemMessage.systemMessage(requestData.systemPrompt().systemPrompt()));
    }
  }

  private void addUserMessagesFromRequest(
      AgentContext agentContext, ChatMemory chatMemory, AgentRequest.AgentRequestData requestData) {
    if (agentContext.isInState(AgentState.WAITING_FOR_TOOL_INPUT)) {
      final var toolCallResults = requestData.tools().toolCallResults();
      if (toolCallResults == null || toolCallResults.isEmpty()) {
        throw new ConnectorException(
            "Agent is waiting for tool input, but tool call results were empty");
      }

      toolCallingHandler.toolCallResultsAsMessages(toolCallResults).forEach(chatMemory::add);
    } else {
      // feed messages with the user input message (first iteration or user follow-up request)
      chatMemory.add(UserMessage.userMessage(requestData.userPrompt().userPrompt()));
    }
  }
}
