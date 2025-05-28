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
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class AiAgentRequestHandlerImpl implements AiAgentRequestHandler {

  private static final int DEFAULT_MAX_MODEL_CALLS = 10;
  private static final int DEFAULT_MAX_MEMORY_MESSAGES = 20;

  private final AdHocToolsSchemaResolver schemaResolver;
  private final Map<String, GatewayToolHandler> gatewayToolHandlers;
  private final AiFrameworkAdapter<?> framework;
  private final AgentResponseHandler responseHandler;

  public AiAgentRequestHandlerImpl(
      AdHocToolsSchemaResolver schemaResolver,
      List<GatewayToolHandler> gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler) {
    this.schemaResolver = schemaResolver;
    this.gatewayToolHandlers = mapGatewayToolHandlers(gatewayToolHandlers);
    this.framework = framework;
    this.responseHandler = responseHandler;
  }

  private Map<String, GatewayToolHandler> mapGatewayToolHandlers(
      List<GatewayToolHandler> gatewayToolHandlers) {
    return gatewayToolHandlers.stream()
        .collect(
            Collectors.toMap(
                GatewayToolHandler::type,
                Function.identity(),
                (g1, g2) -> {
                  throw new IllegalArgumentException(
                      "Duplicate gateway tool handler type: %s".formatted(g1.type()));
                },
                LinkedHashMap::new));
  }

  @Override
  public AgentResponse handleRequest(OutboundConnectorContext context, AgentRequest request) {
    final var agentInitializationResult = initializeAgent(context, request);
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

    var toolCalls = assistantMessage.toolCalls();
    for (GatewayToolHandler gatewayToolHandler : gatewayToolHandlers.values()) {
      toolCalls = gatewayToolHandler.transformToolCalls(agentContext, toolCalls);
    }

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

  private AgentInitializationResult initializeAgent(
      OutboundConnectorContext context, AgentRequest request) {

    AgentContext agentContext =
        Optional.ofNullable(request.data().context()).orElseGet(AgentContext::empty);

    List<ToolCallResult> toolCallResults =
        Optional.ofNullable(request.data().tools())
            .map(ToolsConfiguration::toolCallResults)
            .orElseGet(Collections::emptyList);

    AgentResponse agentResponse = null;

    switch (agentContext.state()) {
      case INITIALIZING -> {
        final var toolsContainerElementId =
            Optional.ofNullable(request.data().tools())
                .map(ToolsConfiguration::containerElementId)
                .filter(id -> !id.isBlank())
                .orElse(null);

        if (toolsContainerElementId != null) {
          final var adHocToolsSchema =
              schemaResolver.resolveSchema(
                  context.getJobContext().getProcessDefinitionKey(), toolsContainerElementId);

          // add tool definitions to agent context
          agentContext = agentContext.withToolDefinitions(adHocToolsSchema.toolDefinitions());

          // initiate tool discovery
          List<ToolCall> toolDiscoveryToolCalls = new ArrayList<>();
          for (GatewayToolHandler gatewayToolHandler : gatewayToolHandlers.values()) {
            GatewayToolDiscoveryContext toolDiscoveryContext =
                gatewayToolHandler.initiateToolDiscovery(
                    agentContext, adHocToolsSchema.gatewayToolDefinitions());

            // update agent context with updated context from discovery (e.g. added properties)
            agentContext = toolDiscoveryContext.agentContext();
            toolDiscoveryToolCalls.addAll(toolDiscoveryContext.toolDiscoveryToolCalls());
          }

          if (toolDiscoveryToolCalls.isEmpty()) {
            // no tool discovery needed
            agentContext = agentContext.withState(AgentState.READY);
          } else {
            agentContext = agentContext.withState(AgentState.TOOL_DISCOVERY);
            agentResponse =
                AgentResponse.builder()
                    .context(agentContext)
                    .toolCalls(
                        toolDiscoveryToolCalls.stream().map(ToolCallProcessVariable::from).toList())
                    .build();
          }
        }
      }

      case TOOL_DISCOVERY -> {
        Map<String, List<ToolCallResult>> groupedByGateway =
            toolCallResults.stream()
                .collect(
                    Collectors.groupingBy(
                        toolCallResult -> {
                          for (GatewayToolHandler gatewayToolHandler :
                              gatewayToolHandlers.values()) {
                            if (gatewayToolHandler.handlesToolDiscoveryResult(toolCallResult)) {
                              return gatewayToolHandler.type();
                            }
                          }

                          return "default";
                        }));

        List<ToolDefinition> mergedToolDefinitions =
            new ArrayList<>(agentContext.toolDefinitions());

        for (Map.Entry<String, List<ToolCallResult>> groupedToolCallResults :
            groupedByGateway.entrySet()) {
          if (groupedToolCallResults.getKey().equals("default")) {
            continue;
          }

          final var gatewayToolHandler = gatewayToolHandlers.get(groupedToolCallResults.getKey());
          List<ToolDefinition> gatewayToolDefinitions =
              gatewayToolHandler.handleToolDiscoveryResults(
                  agentContext, groupedToolCallResults.getValue());
          mergedToolDefinitions.addAll(gatewayToolDefinitions);
        }

        // remaining tool call results not being part of tool discovery
        toolCallResults = groupedByGateway.getOrDefault("default", Collections.emptyList());

        agentContext =
            agentContext
                .withState(AgentState.READY)
                .withToolDefinitions(Collections.unmodifiableList(mergedToolDefinitions));
      }
    }

    return new AgentInitializationResult(agentContext, toolCallResults, agentResponse);
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

        var transformedToolCallResults = toolCallResults;
        for (GatewayToolHandler gatewayToolHandler : gatewayToolHandlers.values()) {
          transformedToolCallResults =
              gatewayToolHandler.transformToolCallResults(agentContext, transformedToolCallResults);
        }

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
