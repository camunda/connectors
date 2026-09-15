/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.agent.AgentTaskRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentTaskExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskV2Request;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * AI Agent Task v2 connector (LLM-provider layer). Service-task flavor; reuses the shared handler.
 */
@OutboundConnector(
    name = "AI Agent Task",
    inputVariables = {AgentProcessVariables.PROVIDER, AgentProcessVariables.DATA},
    type = "io.camunda.agenticai:aiagent:task:2",
    withLease = true)
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.ai-agent-task.v2",
    name = "AI Agent Task",
    description = "Execute a single AI-powered action with tool calling capabilities",
    keywords = {"AI", "AI Agent", "agentic orchestration"},
    documentationRef =
        "https://docs.camunda.io/docs/8.10/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-task/",
    engineVersion = "^8.10",
    version = 1,
    category = @ElementTemplate.Category(id = "aiTools", name = "AI Tools"),
    inputDataClass = AgentTaskV2Request.class,
    outputDataClass = AgentResponse.class,
    defaultResultVariable = AgentProcessVariables.AGENT_RESPONSE,
    propertyGroups = {
      @PropertyGroup(id = "provider", label = "Model provider", openByDefault = false),
      @PropertyGroup(
          id = "advanced-provider-options",
          label = "Advanced provider options",
          tooltip = "Advanced options for fine-tuning the connection to the model provider.",
          openByDefault = false),
      @PropertyGroup(id = "model", label = "Model", openByDefault = false),
      @PropertyGroup(id = "model-options", label = "Model options", openByDefault = false),
      @PropertyGroup(
          id = "systemPrompt",
          label = "System prompt",
          tooltip =
              "A system prompt is a set of foundational instructions given to a model before any user interaction begins. "
                  + "It defines the AI agent’s role, behavior, tone, and communication style, ensuring that responses remain consistent "
                  + "and aligned with the AI agent’s intended purpose. These instructions help shape how the model interprets and responds "
                  + "to user input throughout the conversation.",
          openByDefault = false),
      @PropertyGroup(
          id = "userPrompt",
          label = "User prompt",
          tooltip =
              "A user prompt is the message or question you give to the AI to start or continue a conversation. It tells "
                  + "the AI what you need, whether it's information, help with a task, or just a chat. The AI uses your prompt "
                  + "to understand how to respond.",
          openByDefault = false),
      @PropertyGroup(
          id = "tools",
          label = "Tools",
          tooltip =
              "Tools are optional features the AI Agent can use to perform specific tasks. Configure this if the agent should participate in a tools feedback loop.",
          openByDefault = false),
      @PropertyGroup(
          id = "memory",
          label = "Memory",
          tooltip = "Configuration of the Agent's short-term/conversational memory.",
          openByDefault = false),
      @PropertyGroup(id = "limits", label = "Limits", openByDefault = false),
      @PropertyGroup(
          id = "response",
          label = "Response",
          tooltip =
              "Configuration of the model response format and how to map the model response to the connector result.<br><br>Depending on the selection, the model response will be available as <code>response.responseText</code> or <code>response.responseJson</code>.<br><br>See <a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-task/#response\">documentation</a> for details.",
          openByDefault = false)
    },
    icon = "aiagent.svg")
public class AgentTaskV2Function implements AgentConnectorFunction {
  private static final String LINKED_RESOURCES_HEADER = "linkedResources";
  private static final String SYSTEM_PROMPT_RESOURCE_TYPE = "system-prompt";
  private static final String SYSTEM_PROMPT_LINK_NAME = "systemPrompt";
  private static final String ERROR_CODE_LINKED_SYSTEM_PROMPT =
      "LINKED_SYSTEM_PROMPT_RESOLUTION_ERROR";

  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final AgentTaskRequestHandler agentRequestHandler;
  private final CamundaClient camundaClient;
  private final ObjectMapper objectMapper;

  public AgentTaskV2Function(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AgentTaskRequestHandler agentRequestHandler,
      CamundaClient camundaClient,
      ObjectMapper objectMapper) {
    this.toolElementsResolver = toolElementsResolver;
    this.agentRequestHandler = agentRequestHandler;
    this.camundaClient = camundaClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public AgentTaskConnectorResponse execute(OutboundConnectorContext context) {
    var request = context.bindVariables(AgentTaskV2Request.class);
    var systemPrompt =
        composeSystemPrompt(
            request.data().systemPrompt().prompt(), context.getJobContext().getCustomHeaders());
    var executionContext =
        new AgentTaskExecutionContext(
            context.getJobContext(),
            request.data(),
            request.provider(),
            toolElementsResolver,
            new SystemPromptConfiguration(systemPrompt));
    return agentRequestHandler.handleRequest(executionContext);
  }

  private String composeSystemPrompt(String inlinePrompt, Map<String, String> customHeaders) {
    var linkedPrompt = resolveLinkedSystemPrompt(customHeaders);
    if (StringUtils.isBlank(linkedPrompt)) {
      return inlinePrompt;
    }
    if (StringUtils.isBlank(inlinePrompt)) {
      return linkedPrompt;
    }
    return inlinePrompt + "\n\n" + linkedPrompt;
  }

  private @Nullable String resolveLinkedSystemPrompt(Map<String, String> customHeaders) {
    var rawLinkedResources = customHeaders.get(LINKED_RESOURCES_HEADER);
    if (StringUtils.isBlank(rawLinkedResources)) {
      return null;
    }

    final List<LinkedResource> linkedResources;
    try {
      linkedResources =
          objectMapper.readValue(rawLinkedResources, new TypeReference<List<LinkedResource>>() {});
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          ERROR_CODE_LINKED_SYSTEM_PROMPT,
          "Failed to parse linked resource metadata from the activated job.",
          e);
    }

    var systemPromptResources =
        linkedResources.stream()
            .filter(
                resource ->
                    SYSTEM_PROMPT_RESOURCE_TYPE.equals(resource.resourceType())
                        && SYSTEM_PROMPT_LINK_NAME.equals(resource.linkName()))
            .toList();
    if (systemPromptResources.isEmpty()) {
      return null;
    }
    if (systemPromptResources.size() > 1) {
      throw new ConnectorException(
          ERROR_CODE_LINKED_SYSTEM_PROMPT,
          "The activated job contains multiple linked system prompt resources.");
    }

    var resourceKey = systemPromptResources.getFirst().resourceKey();
    if (resourceKey == null) {
      throw new ConnectorException(
          ERROR_CODE_LINKED_SYSTEM_PROMPT,
          "The linked system prompt resource has no resolved resource key.");
    }
    try {
      return camundaClient.newResourceContentBinaryGetRequest(resourceKey).execute();
    } catch (Exception e) {
      throw new ConnectorException(
          ERROR_CODE_LINKED_SYSTEM_PROMPT,
          "Failed to retrieve linked system prompt resource with key %d.".formatted(resourceKey),
          e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record LinkedResource(@Nullable Long resourceKey, String resourceType, String linkName) {}
}
