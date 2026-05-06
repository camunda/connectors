/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import java.util.concurrent.CompletableFuture;

/**
 * High-level facade called by {@code BaseAgentRequestHandler} once Phase 1 lands. Resolves the
 * model and capabilities via {@link ChatModelApiRegistry}, applies the tool-result strategy,
 * assembles the {@link ChatRequest} from the runtime memory, and dispatches to {@link
 * ChatModelApi#complete}.
 *
 * <p>Replaces today's {@code AiFrameworkAdapter} call site. Mirroring its signature keeps the
 * cutover diff small.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Not yet wired into the runtime.
 */
public interface ChatClient {

  CompletableFuture<ChatResponse> chat(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory runtimeMemory,
      ChatStreamListener listener);
}
