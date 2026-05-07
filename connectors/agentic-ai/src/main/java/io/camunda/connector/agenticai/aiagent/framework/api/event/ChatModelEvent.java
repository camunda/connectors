/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api.event;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import org.springframework.lang.Nullable;

/**
 * Discriminated stream events emitted by a {@code ChatModelApi} implementation while driving a
 * provider's streaming endpoint. Listeners receive every event in order; {@link DoneEvent} carries
 * the assembled {@link ChatResponse}, while {@link ErrorEvent} carries the error message plus any
 * partial content / usage accumulated before the failure.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public sealed interface ChatModelEvent
    permits ChatModelEvent.StartEvent,
        ChatModelEvent.TextStartEvent,
        ChatModelEvent.TextDeltaEvent,
        ChatModelEvent.TextEndEvent,
        ChatModelEvent.ReasoningStartEvent,
        ChatModelEvent.ReasoningDeltaEvent,
        ChatModelEvent.ReasoningEndEvent,
        ChatModelEvent.ToolCallStartEvent,
        ChatModelEvent.ToolCallArgumentsDeltaEvent,
        ChatModelEvent.ToolCallEndEvent,
        ChatModelEvent.UsageEvent,
        ChatModelEvent.DoneEvent,
        ChatModelEvent.ErrorEvent {

  record StartEvent(@Nullable String apiFamily, @Nullable String modelId)
      implements ChatModelEvent {}

  record TextStartEvent(int blockIndex) implements ChatModelEvent {}

  record TextDeltaEvent(int blockIndex, String delta) implements ChatModelEvent {}

  record TextEndEvent(int blockIndex) implements ChatModelEvent {}

  record ReasoningStartEvent(int blockIndex) implements ChatModelEvent {}

  record ReasoningDeltaEvent(int blockIndex, String delta, @Nullable String signatureDelta)
      implements ChatModelEvent {}

  record ReasoningEndEvent(int blockIndex) implements ChatModelEvent {}

  record ToolCallStartEvent(int blockIndex, String toolCallId, String toolName)
      implements ChatModelEvent {}

  record ToolCallArgumentsDeltaEvent(int blockIndex, String argumentsDelta)
      implements ChatModelEvent {}

  record ToolCallEndEvent(int blockIndex) implements ChatModelEvent {}

  record UsageEvent(AgentMetrics.TokenUsage usage) implements ChatModelEvent {}

  record DoneEvent(ChatResponse response) implements ChatModelEvent {}

  record ErrorEvent(
      String errorMessage,
      @Nullable ChatResponse partialResponse,
      @Nullable AgentMetrics.TokenUsage partialUsage)
      implements ChatModelEvent {}
}
