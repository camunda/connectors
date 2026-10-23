/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

public record AgentSubProcessRequestData(
    @Valid @NotNull PromptTemplateConfiguration.SystemPromptTemplateConfiguration systemPrompt,
    @Valid @NotNull PromptTemplateConfiguration.UserPromptTemplateConfiguration userPrompt,
    @Valid @Nullable MemoryTemplateConfiguration memory,
    @Valid @Nullable LimitsTemplateConfiguration limits,
    @Valid @Nullable EventHandlingTemplateConfiguration events,
    @Valid @Nullable AgentSubProcessResponseConfiguration response) {}
