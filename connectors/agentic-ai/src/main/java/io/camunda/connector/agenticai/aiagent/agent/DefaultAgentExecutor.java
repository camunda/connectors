/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationLoadResult;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class DefaultAgentExecutor implements AgentExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAgentExecutor.class);

  private static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;

  private final ConversationStoreRegistry conversationStoreRegistry;
  private final AgentLimitsValidator limitsValidator;
  private final AgentMessagesHandler messagesHandler;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AiFrameworkAdapter<?> framework;
  private final AgentResponseHandler responseHandler;

  public DefaultAgentExecutor(
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler) {
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.limitsValidator = limitsValidator;
    this.messagesHandler = messagesHandler;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.framework = framework;
    this.responseHandler = responseHandler;
  }

  @Override
  public AgentExecutionResult execute(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults) {
    final var conversationStore =
        conversationStoreRegistry.getConversationStore(executionContext, agentContext);
    final var session = conversationStore.createSession(executionContext, agentContext);

    // load messages from conversation store
    LOGGER.trace("Loading previous conversation (if any) from session");
    final var loadResult = session.loadMessages(agentContext);

    // if the store reconciled from a newer version, derive response directly
    if (loadResult.reconciledFromStore()) {
      LOGGER.debug("Store reconciled from newer version, deriving response from conversation");
      return deriveReconciledResponse(executionContext, agentContext, loadResult, session);
    }

    // set up memory and populate with loaded messages
    final var runtimeMemory =
        new MessageWindowRuntimeMemory(
            Optional.ofNullable(executionContext.memory())
                .map(MemoryConfiguration::contextWindowSize)
                .orElse(DEFAULT_CONTEXT_WINDOW_SIZE));

    runtimeMemory.addMessages(loadResult.messages());

    // validate configured limits
    LOGGER.trace("Validating configured limits for agent execution");
    limitsValidator.validateConfiguredLimits(executionContext, agentContext);

    // update memory with system message
    LOGGER.trace("Adding system message (if necessary)");
    messagesHandler.addSystemMessage(
        executionContext, agentContext, runtimeMemory, executionContext.systemPrompt());

    // update memory with user messages/tool call responses
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Adding user messages including the following {} tool call results: {}",
          toolCallResults.size(),
          toolCallResults.stream().map(tcr -> Pair.of(tcr.id(), tcr.name())).toList());
    }

    final var userMessages =
        messagesHandler.addUserMessages(
            executionContext,
            agentContext,
            runtimeMemory,
            executionContext.userPrompt(),
            toolCallResults);

    // check if we're actually able to call the model, abort early otherwise
    if (CollectionUtils.isEmpty(userMessages)) {
      LOGGER.debug("No user messages added, returning no-op result");
      return new AgentExecutionResult(null, session);
    }

    // detect interrupted tool calls and propagate to execution context
    handleInterruptedToolCalls(executionContext, userMessages);

    // call framework with memory
    LOGGER.debug("Executing chat request with AI framework");
    final var frameworkChatResponse =
        framework.executeChatRequest(executionContext, agentContext, runtimeMemory);
    agentContext = frameworkChatResponse.agentContext();

    final var assistantMessage = frameworkChatResponse.assistantMessage();
    LOGGER.debug(
        "Received assistant message containing {} tool call requests",
        assistantMessage.toolCalls() != null ? assistantMessage.toolCalls().size() : 0);
    runtimeMemory.addMessage(assistantMessage);

    // apply potential gateway tool call transformations & map tool call to process
    // variable format
    final var toolCalls =
        gatewayToolHandlers.transformToolCalls(agentContext, assistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    // store messages to conversation session
    LOGGER.debug("Storing messages to conversation session");
    agentContext = session.storeMessages(agentContext, runtimeMemory.allMessages());

    final var response =
        responseHandler.createResponse(
            executionContext, agentContext, assistantMessage, processVariableToolCalls);
    return AgentExecutionResult.of(response, session);
  }

  private AgentExecutionResult deriveReconciledResponse(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      ConversationLoadResult loadResult,
      ConversationSession session) {
    final var messages = loadResult.messages();
    // find last assistant message
    final var lastAssistantMessage =
        messages.reversed().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException("Reconciled conversation has no assistant message"));

    final var toolCalls =
        gatewayToolHandlers.transformToolCalls(agentContext, lastAssistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    // store to sync agentContext version with store
    agentContext = session.storeMessages(agentContext, messages);

    final var response =
        responseHandler.createResponse(
            executionContext, agentContext, lastAssistantMessage, processVariableToolCalls);
    return AgentExecutionResult.of(response, session);
  }

  private void handleInterruptedToolCalls(
      AgentExecutionContext executionContext, List<Message> addedUserMessages) {
    final boolean hasInterruptedToolCalls =
        addedUserMessages.stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .flatMap(msg -> msg.results().stream())
            .anyMatch(
                result ->
                    Boolean.TRUE.equals(
                        result
                            .properties()
                            .getOrDefault(ToolCallResult.PROPERTY_INTERRUPTED, false)));

    if (hasInterruptedToolCalls
        && executionContext instanceof JobWorkerAgentExecutionContext jobWorkerContext) {
      jobWorkerContext.setCancelRemainingInstances(true);
    }
  }
}
