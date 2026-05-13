/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.model.message.AssistantMessage;

/**
 * Output of {@link ChatModelApi#complete}. Carries the assembled assistant message; {@link
 * AssistantMessage#stopReason()} and {@link AssistantMessage#usage()} convey the normalized stop
 * reason and per-call token usage. Model-side terminal failures (refusal, content filter, malformed
 * tool-use) populate the message with {@code stopReason = ERROR}; transport / SDK / auth failures
 * complete the future exceptionally instead.
 *
 * <p>Part of the ADR-005 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public record ChatResponse(AssistantMessage assistantMessage) {}
