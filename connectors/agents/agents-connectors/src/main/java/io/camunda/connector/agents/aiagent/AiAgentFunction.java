/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.aiagent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.camunda.connector.agents.aiagent.converter.AgentContextMessageSerializer;
import io.camunda.connector.agents.aiagent.model.AgentContext;
import io.camunda.connector.agents.aiagent.model.AgentResponse;
import io.camunda.connector.agents.aiagent.model.request.AgentRequest;
import io.camunda.connector.agents.aiagent.model.request.AgentRequest.AgentRequestData;
import io.camunda.connector.agents.aiagent.provider.ChatModelFactory;
import io.camunda.connector.agents.aiagent.tools.ToolCallingHandler;
import io.camunda.connector.agents.aiagent.tools.ToolSpecificationConverter;
import io.camunda.connector.agents.core.AgentsApplicationContextHolder;
import io.camunda.connector.agents.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "AI Agent",
    inputVariables = {"provider", "data"},
    type = "io.camunda.agents:aiagent:1")
@ElementTemplate(
    id = "io.camunda.connectors.agents.aiagent.v1",
    name = "AI Agent",
    description = "AI Agent connector",
    inputDataClass = AgentRequest.class,
    version = 1,
    propertyGroups = {
      @PropertyGroup(id = "provider", label = "Provider"),
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "model", label = "Model"),
      @PropertyGroup(id = "parameters", label = "Model Parameters"),
      @PropertyGroup(id = "context", label = "Context"),
      @PropertyGroup(id = "prompt", label = "Prompt"),
      @PropertyGroup(id = "tools", label = "Tools"),
      @PropertyGroup(id = "history", label = "History"),
      @PropertyGroup(id = "guardrails", label = "Guardrails"),
    },
    documentationRef = "https://example.com",
    icon = "aiagent.svg")
public class AiAgentFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(AiAgentFunction.class);

  private static final int DEFAULT_MAX_MODEL_CALLS = 10;
  private static final int DEFAULT_MAX_HISTORY_MESSAGES = 20;

  private final ChatModelFactory chatModelFactory;
  private final AgentContextMessageSerializer agentContextMessageSerializer;
  private final ToolCallingHandler toolCallingHandler;

  public AiAgentFunction() {
    this(
        new ChatModelFactory(),
        contextMessageSerializerFromStaticContext(),
        toolCallingHandlerFromStaticContext());
  }

  public AiAgentFunction(
      ChatModelFactory chatModelFactory,
      AgentContextMessageSerializer agentContextMessageSerializer,
      ToolCallingHandler toolCallingHandler) {
    this.chatModelFactory = chatModelFactory;
    this.agentContextMessageSerializer = agentContextMessageSerializer;
    this.toolCallingHandler = toolCallingHandler;
  }

  private static AgentContextMessageSerializer contextMessageSerializerFromStaticContext() {
    return new AgentContextMessageSerializer(
        AgentsApplicationContextHolder.currentContext().objectMapper());
  }

  private static ToolCallingHandler toolCallingHandlerFromStaticContext() {
    final var applicationContext = AgentsApplicationContextHolder.currentContext();

    return new ToolCallingHandler(
        applicationContext.objectMapper(),
        new AdHocToolsSchemaResolver(applicationContext.camundaClient()),
        new ToolSpecificationConverter());
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final AgentRequest request = context.bindVariables(AgentRequest.class);
    final AgentRequestData requestData = request.data();

    // initialize configured model
    final ChatLanguageModel chatModel = chatModelFactory.createChatModel(request);

    // set up history and load from context if available
    final ChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
    final ChatMemory chatMemory =
        MessageWindowChatMemory.builder()
            .maxMessages(
                Optional.ofNullable(requestData.history().maxMessages())
                    .orElse(DEFAULT_MAX_HISTORY_MESSAGES))
            .chatMemoryStore(chatMemoryStore)
            .build();

    // load context and put messages to history
    final AgentContext agentContext =
        Optional.ofNullable(requestData.context()).orElseGet(AgentContext::empty);
    agentContextMessageSerializer.loadFromAgentContext(agentContext).forEach(chatMemory::add);

    // check configured guardrails
    checkGuardrails(requestData, agentContext);

    // update history with system + new user messages/tool call responses
    addSystemPromptIfNecessary(chatMemory, requestData);
    addUserMessagesFromRequest(chatMemory, requestData);

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

    // update context
    final var updatedContext =
        agentContext
            .withMetrics(
                agentContext
                    .metrics()
                    .incrementModelCalls(1)
                    .incrementTokenUsage(chatResponse.tokenUsage().totalTokenCount()))
            .withHistory(
                agentContextMessageSerializer.asAgentContextHistory(chatMemory.messages()));

    return new AgentResponse(updatedContext, updatedContext.history().getLast(), toolsToCall);
  }

  private void addSystemPromptIfNecessary(ChatMemory chatMemory, AgentRequestData requestData) {
    // add system prompt if this is the first request
    if (chatMemory.messages().isEmpty()) {
      chatMemory.add(SystemMessage.systemMessage(requestData.systemPrompt().systemPrompt()));
    }
  }

  private void addUserMessagesFromRequest(ChatMemory chatMemory, AgentRequestData requestData) {
    // model requested tool executions in previous request -> map results to new messages
    if (!chatMemory.messages().isEmpty()
        && chatMemory.messages().getLast() instanceof AiMessage lastAiMessage
        && lastAiMessage.hasToolExecutionRequests()
        && requestData.tools().toolCallResults() != null) {
      toolCallingHandler
          .toolCallResultsAsMessages(requestData.tools().toolCallResults())
          .forEach(chatMemory::add);
    } else {
      // feed messages with the user input message (first iteration or user follow-up request)
      chatMemory.add(UserMessage.userMessage(requestData.userPrompt().userPrompt()));
    }
  }

  private void checkGuardrails(AgentRequestData requestData, AgentContext agentContext) {
    // naive guardrail implementation
    final int maxModelCalls =
        Optional.ofNullable(requestData.guardrails().maxModelCalls())
            .orElse(DEFAULT_MAX_MODEL_CALLS);
    if (agentContext.metrics().modelCalls() >= maxModelCalls) {
      throw new ConnectorException("Maximum number of model calls reached");
    }
  }
}
