/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Inputs to a {@link ChatModelApi#complete} call assembled by {@code ChatClient}: the conversation
 * messages (system message inline at the head when present), resolved tool definitions, and the
 * requested response format. Per-call tunables (max output tokens, reasoning, cache retention,
 * vendor escape hatches) live on {@link ChatOptions} so a request can be reused while options vary.
 *
 * <p>{@code responseFormat} reuses the connector-config type {@link ResponseFormatConfiguration}
 * directly. Implementations translate the {@code Json}/{@code Text} variants onto the provider's
 * native shape; providers without a native structured-output mode (Anthropic Messages today) treat
 * the JSON variant as best-effort and rely on the system prompt to constrain output.
 *
 * <p>Part of the ADR-005 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public record ChatRequest(
    List<Message> messages,
    List<ToolDefinition> toolDefinitions,
    @Nullable ResponseFormatConfiguration responseFormat) {}
