/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.v2.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.versioning.VersionedAgentContextDeserializer;
import io.camunda.connector.api.annotation.FEEL;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AgentSubProcessV2Request(
    @JsonProperty("adHocSubProcessElements") List<AdHocToolElement> toolElements,
    @FEEL @Valid @JsonDeserialize(using = VersionedAgentContextDeserializer.class)
        AgentContext agentContext,
    List<ToolCallResult> toolCallResults,
    @Valid @NotNull ProviderConfiguration provider,
    @Valid @NotNull AgentSubProcessRequestData data) {}
