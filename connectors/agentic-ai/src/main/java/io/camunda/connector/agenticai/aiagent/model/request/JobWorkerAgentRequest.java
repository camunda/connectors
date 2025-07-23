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
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record JobWorkerAgentRequest(
    @JsonProperty("adHocSubProcessElements") List<AdHocToolElement> toolElements,
    @Valid AgentContext agentContext,
    List<ToolCallResult> toolCallResults,
    @Valid @NotNull ProviderConfiguration provider,
    @Valid @NotNull OutboundConnectorAgentRequestData data) {}
