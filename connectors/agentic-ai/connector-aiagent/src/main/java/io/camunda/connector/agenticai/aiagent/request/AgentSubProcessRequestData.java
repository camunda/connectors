/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.request;

import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

public record AgentSubProcessRequestData(
    @Valid @NotNull PromptConfiguration.SystemPromptConfiguration systemPrompt,
    @Valid @NotNull PromptConfiguration.UserPromptConfiguration userPrompt,
    @Valid @Nullable MemoryTemplateConfiguration memory,
    @Valid @Nullable LimitsConfiguration limits,
    @Valid @Nullable EventHandlingConfiguration events,
    @Valid @Nullable AgentSubProcessResponseConfiguration response) {}
