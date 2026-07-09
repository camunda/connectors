/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest.JobWorkerAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.LlmProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * v2 AI Agent Sub-process request: wire-format-first chat-model config + the shared v1 request
 * data.
 */
public record JobWorkerAgentRequestV2(
    @JsonProperty("adHocSubProcessElements") List<AdHocToolElement> toolElements,
    @FEEL
        @TemplateProperty(
            label = "Agent context",
            group = "memory",
            id = "agentContext",
            description =
                "Initial agent context from previous interactions. Avoid reusing context variables across agents to prevent issues with stale data or tool access.",
            tooltip =
                "The agent context variable containing all relevant data for the agent to support the feedback loop between "
                    + "user requests, tool calls and LLM responses. Make sure this variable points to the <code>context</code> "
                    + "variable which is returned from the agent response. "
                    + "<a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-subprocess/\" target=\"_blank\">See documentation</a> "
                    + "for details.",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.required)
        @Valid
        AgentContext agentContext,
    List<ToolCallResult> toolCallResults,
    @Valid @NotNull LlmProviderConfiguration configuration,
    @Valid @NotNull JobWorkerAgentRequestData data) {}
