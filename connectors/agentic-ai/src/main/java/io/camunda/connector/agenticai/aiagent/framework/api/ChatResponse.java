/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.StopReason;
import org.springframework.lang.Nullable;

/**
 * Output of {@link ChatModelApi#complete}. Carries the assembled assistant message plus a
 * normalised {@link StopReason} and per-call {@link AgentMetrics.TokenUsage}. {@code errorMessage}
 * is populated only for model-side terminal failures (refusal, content filter, malformed tool-use)
 * where {@code stopReason == ERROR}; transport / SDK / auth failures complete the future
 * exceptionally instead.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Not yet wired into the runtime.
 */
public record ChatResponse(
    AssistantMessage assistantMessage,
    @Nullable StopReason stopReason,
    @Nullable AgentMetrics.TokenUsage usage,
    @Nullable String errorMessage) {}
