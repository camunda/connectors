/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.framework.api.event.ChatModelEvent;

/**
 * In-process observability hook invoked for every {@link ChatModelEvent} produced while a {@link
 * ChatModelApi} drives a provider's streaming endpoint. Listeners default to {@link #NOOP}; the
 * listener is intentionally not exposed as a reactive type — the public chat surface remains a
 * blocking {@code CompletableFuture<ChatResponse>}.
 *
 * <p>Part of the ADR-005 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
@FunctionalInterface
public interface ChatStreamListener {

  ChatStreamListener NOOP = event -> {};

  void onEvent(ChatModelEvent event);
}
