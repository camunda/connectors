/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_IN_INVALID_STATE;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import dev.langchain4j.model.input.PromptTemplate;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class AiAgentRequestHandlerImpl implements AiAgentRequestHandler {

  private static final int DEFAULT_MAX_MODEL_CALLS = 10;
  private static final int DEFAULT_MAX_MEMORY_MESSAGES = 20;

  private final AgentInitializer agentInitializer;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AiFrameworkAdapter<?> framework;
  private final AgentResponseHandler responseHandler;

  public AiAgentRequestHandlerImpl(
      AgentInitializer agentInitializer,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler) {
    this.agentInitializer = agentInitializer;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.framework = framework;
    this.responseHandler = responseHandler;
  }

  @Override
  public AgentResponse handleRequest(OutboundConnectorContext context, AgentRequest request) {
    final var agentInitializationResult = agentInitializer.initializeAgent(context, request);
    if (agentInitializationResult.agentResponse() != null) {
      // return agent response if needed (e.g. tool discovery tool calls before calling the LLM)
      return agentInitializationResult.agentResponse();
    }

    AgentContext agentContext = agentInitializationResult.agentContext();

    // set up memory and load from context if available
    final var runtimeMemory =
        new MessageWindowRuntimeMemory(
            Optional.ofNullable(request.data().memory())
                .map(MemoryConfiguration::maxMessages)
                .orElse(DEFAULT_MAX_MEMORY_MESSAGES));

    final var conversationStore = new InProcessConversationStore();
    conversationStore.loadIntoRuntimeMemory(context, agentContext, runtimeMemory);

    // check configured limits
    checkLimits(request.data(), agentContext);

    // update memory with system message + new user messages/tool call responses
    addSystemPromptIfNecessary(runtimeMemory, request.data());
    addUserMessagesFromRequest(
        agentContext, runtimeMemory, request.data(), agentInitializationResult.toolCallResults());

    // call framework with memory
    AiFrameworkChatResponse<?> frameworkChatResponse =
        framework.executeChatRequest(request, agentContext, runtimeMemory);
    agentContext = frameworkChatResponse.agentContext();

    final var assistantMessage = frameworkChatResponse.assistantMessage();
    runtimeMemory.addMessage(assistantMessage);

    // apply potential gateway tool call transformations & map tool call to process variable format
    var toolCalls =
        gatewayToolHandlers.transformToolCalls(agentContext, assistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    final var nextAgentState =
        toolCalls.isEmpty() ? AgentState.READY : AgentState.WAITING_FOR_TOOL_INPUT;

    // store memory to context and update the next agent state based on tool calls
    agentContext =
        conversationStore
            .storeFromRuntimeMemory(context, agentContext, runtimeMemory)
            .withState(nextAgentState);

    return responseHandler.createResponse(
        request, agentContext, assistantMessage, processVariableToolCalls);
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
      RuntimeMemory memory, AgentRequest.AgentRequestData requestData) {
    if (StringUtils.isNotBlank(requestData.systemPrompt().prompt())) {
      // memory will take care of replacing any existing system message if already present
      memory.addMessage(
          SystemMessage.systemMessage(promptFromConfiguration(requestData.systemPrompt())));
    }
  }

  private void addUserMessagesFromRequest(
      AgentContext agentContext,
      RuntimeMemory memory,
      AgentRequest.AgentRequestData requestData,
      List<ToolCallResult> toolCallResults) {
    // throw an error when receiving tool call results on an empty context as
    // most likely this is a modeling error
    if (agentContext.conversation() == null && !toolCallResults.isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT,
          "Agent received tool call results, but the agent context was empty (no previous conversation). Is the context configured correctly?");
    }

    switch (agentContext.state()) {
      // add tool call results
      case WAITING_FOR_TOOL_INPUT -> {
        if (toolCallResults.isEmpty()) {
          throw new ConnectorException(
              ERROR_CODE_WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS,
              "Agent is waiting for tool input, but tool call results were empty. Is the tool feedback loop configured correctly?");
        }

        var transformedToolCallResults =
            gatewayToolHandlers.transformToolCallResults(agentContext, toolCallResults);

        memory.addMessage(
            ToolCallResultMessage.builder().results(transformedToolCallResults).build());
      }

      // add user messages
      case READY -> {
        final var userPrompt = requestData.userPrompt();
        final var content = new ArrayList<Content>();

        // add user prompt text
        if (StringUtils.isNotBlank(userPrompt.prompt())) {
          content.add(textContent(promptFromConfiguration(userPrompt)));
        }

        // add documents
        Optional.ofNullable(userPrompt.documents()).orElseGet(Collections::emptyList).stream()
            .map(DocumentContent::new)
            .forEach(content::add);

        if (content.isEmpty()) {
          throw new ConnectorException(
              ERROR_CODE_NO_USER_MESSAGE_CONTENT,
              "Agent is in state %s but no user prompt (no text, no documents) to add."
                  .formatted(agentContext.state()));
        }

        memory.addMessage(UserMessage.builder().content(content).build());
      }

      default ->
          throw new ConnectorException(
              ERROR_CODE_AGENT_IN_INVALID_STATE,
              "Agent is in invalid state '%s', not ready to for adding user messages"
                  .formatted(agentContext.state()));
    }
  }

  private String promptFromConfiguration(PromptConfiguration promptConfiguration) {
    // TODO replace L4j prompt with something more powerful?
    final var parameters =
        Optional.ofNullable(promptConfiguration.parameters()).orElseGet(Collections::emptyMap);
    return PromptTemplate.from(promptConfiguration.prompt()).apply(parameters).text();
  }
}
