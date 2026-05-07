/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import java.util.concurrent.CompletableFuture;

/**
 * Per-job model client with the resolved provider configuration baked in. Created on demand by a
 * {@link ChatModelApiFactory} and reused across capability lookups, tool-result strategy
 * application, and the {@link #complete} call within a single request.
 *
 * <p>Implementations drive the underlying SDK's streaming endpoint internally and expose a blocking
 * {@link CompletableFuture} surface to callers. The optional {@link ChatStreamListener} receives
 * discriminated stream events for in-process observability.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public interface ChatModelApi {

  ModelCapabilities capabilities();

  CompletableFuture<ChatResponse> complete(
      ChatRequest request, ChatOptions options, ChatStreamListener listener);
}
