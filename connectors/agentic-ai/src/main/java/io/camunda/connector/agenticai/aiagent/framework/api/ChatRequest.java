/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Inputs to a {@link ChatModelApi#complete} call assembled by {@code ChatClient}. Carries the
 * conversation messages, an optional system prompt, and the resolved tool definitions; per-call
 * tunables (max output tokens, stop sequences, reasoning, cache retention, vendor escape hatches)
 * live on {@link ChatOptions} so a request can be reused while options vary.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry — concrete fields will be finalised when the first native {@code
 * ChatModelApi} implementation lands.
 */
public record ChatRequest(
    List<Message> messages, @Nullable String systemPrompt, List<ToolDefinition> tools) {}
