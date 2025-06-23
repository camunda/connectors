/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import dev.langchain4j.model.input.PromptTemplate;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
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
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.ToolsConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
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

  private static final String ERROR_CODE_NO_USER_MESSAGE_CONTENT = "NO_USER_MESSAGE_CONTENT";
  private static final String ERROR_CODE_WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS =
      "WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS";
  private static final String ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT =
      "TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT";
  private static final String ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED =
      "MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED";

  private final AdHocToolsSchemaResolver schemaResolver;
  private final AiFrameworkAdapter<?> framework;

  public AiAgentRequestHandlerImpl(
      AdHocToolsSchemaResolver schemaResolver, AiFrameworkAdapter<?> framework) {
    this.schemaResolver = schemaResolver;
    this.framework = framework;
  }

  @Override
  public AgentResponse handleRequest(OutboundConnectorContext context, AgentRequest request) {
    final AgentRequest.AgentRequestData requestData = request.data();
    AgentContext agentContext = initializeAgentContext(context, request);

    // set up memory and load from context if available
    final var runtimeMemory =
        new MessageWindowRuntimeMemory(
            Optional.ofNullable(requestData.memory())
                .map(MemoryConfiguration::maxMessages)
                .orElse(DEFAULT_MAX_MEMORY_MESSAGES));

    final var conversationStore = new InProcessConversationStore();
    conversationStore.loadIntoRuntimeMemory(context, agentContext, runtimeMemory);

    // check configured limits
    checkLimits(requestData, agentContext);

    // update memory with system message + new user messages/tool call responses
    addSystemPromptIfNecessary(runtimeMemory, requestData);
    addUserMessagesFromRequest(agentContext, runtimeMemory, requestData);

    // call framework with memory
    AiFrameworkChatResponse<?> frameworkChatResponse =
        framework.executeChatRequest(request, agentContext, runtimeMemory);
    agentContext = frameworkChatResponse.agentContext();

    final var assistantMessage = frameworkChatResponse.assistantMessage();
    runtimeMemory.addMessage(assistantMessage);

    final var toolCalls =
        assistantMessage.toolCalls().stream().map(ToolCallProcessVariable::from).toList();
    final var nextAgentState =
        toolCalls.isEmpty() ? AgentState.READY : AgentState.WAITING_FOR_TOOL_INPUT;

    // store memory to context and update the next agent state based on tool calls
    agentContext =
        conversationStore
            .storeFromRuntimeMemory(context, agentContext, runtimeMemory)
            .withState(nextAgentState);

    return createResponse(request, agentContext, toolCalls, assistantMessage);
  }

  private AgentResponse createResponse(
      AgentRequest request,
      AgentContext agentContext,
      List<ToolCallProcessVariable> toolCalls,
      AssistantMessage assistantMessage) {
    final var responseConfiguration =
        Optional.ofNullable(request.data().response())
            // default to text content only if not configured
            .orElseGet(() -> new ResponseConfiguration(true, false));

    final var builder = AgentResponse.builder().context(agentContext).toolCalls(toolCalls);

    if (responseConfiguration.includeText()) {
      assistantMessage.content().stream()
          .filter(c -> c instanceof TextContent)
          .map(c -> ((TextContent) c).text())
          .findFirst()
          .ifPresent(builder::responseText);
    }

    if (responseConfiguration.includeAssistantMessage()) {
      builder.responseMessage(assistantMessage);
    }

    return builder.build();
  }

  private AgentContext initializeAgentContext(
      OutboundConnectorContext context, AgentRequest request) {
    if (request.data().context() != null) {
      return request.data().context();
    }

    var toolDefinitions = loadToolDefinitions(context, request);
    return AgentContext.empty().withToolDefinitions(toolDefinitions);
  }

  private List<ToolDefinition> loadToolDefinitions(
      OutboundConnectorContext context, AgentRequest request) {
    String containerElementId =
        Optional.ofNullable(request.data().tools())
            .map(ToolsConfiguration::containerElementId)
            .filter(id -> !id.isBlank())
            .orElse(null);

    if (containerElementId == null) {
      return Collections.emptyList();
    }

    return schemaResolver
        .resolveSchema(context.getJobContext().getProcessDefinitionKey(), containerElementId)
        .toolDefinitions();
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
      AgentContext agentContext, RuntimeMemory memory, AgentRequest.AgentRequestData requestData) {
    final var toolCallResults =
        Optional.ofNullable(requestData.tools())
            .map(ToolsConfiguration::toolCallResults)
            .filter(tcr -> !tcr.isEmpty())
            .orElseGet(Collections::emptyList);

    // throw an error when receiving tool call results on an empty context as
    // most likely this is a modeling error
    if (agentContext.conversation() == null && !toolCallResults.isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT,
          "Agent received tool call results, but the agent context was empty (no previous conversation). Is the context configured correctly?");
    }

    // add tool call results to chat memory
    if (agentContext.state() == AgentState.WAITING_FOR_TOOL_INPUT) {
      if (toolCallResults.isEmpty()) {
        throw new ConnectorException(
            ERROR_CODE_WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS,
            "Agent is waiting for tool input, but tool call results were empty. Is the tool feedback loop configured correctly?");
      }

      memory.addMessage(ToolCallResultMessage.builder().results(toolCallResults).build());
    } else {
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
  }

  private String promptFromConfiguration(PromptConfiguration promptConfiguration) {
    // TODO replace L4j prompt with something more powerful?
    final var parameters =
        Optional.ofNullable(promptConfiguration.parameters()).orElseGet(Collections::emptyMap);
    return PromptTemplate.from(promptConfiguration.prompt()).apply(parameters).text();
  }
}
