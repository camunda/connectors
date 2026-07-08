/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

/**
 * Provider-neutral, normalized finish reason. Diagnostics + a thin predicate surface only — never
 * load-bearing for control flow (that keys off {@link AssistantMessage#hasToolCalls()}). The raw
 * vendor value is always preserved in {@link AssistantMessage#metadata()}. Do NOT exhaustively
 * switch on this enum: it is part of the persisted message contract, so new values must remain
 * non-breaking. Continuation states (e.g. Anthropic {@code pause_turn}) are NOT represented here —
 * see the {@code Continuation} chat result. See ADR 009 §3.
 */
public enum StopReason {
  STOP,
  LENGTH,
  TOOL_USE,
  CONTENT_FILTERED,
  GUARDRAIL,
  ERROR,
  ABORTED,
  UNKNOWN
}
