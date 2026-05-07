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

/**
 * High-level facade called by {@code BaseAgentRequestHandler}. Resolves the {@link ChatModelApi}
 * for the request, applies the tool-result strategy, assembles a {@link ChatRequest} + {@link
 * ChatOptions} from the runtime memory and the execution / agent context, dispatches to {@link
 * ChatModelApi#complete}, joins the resulting future, increments {@link AgentContext#metrics()}
 * based on the assistant message, and wraps everything in a {@link ChatClientResult}.
 *
 * <p>The asynchronous nature of {@link ChatModelApi#complete} is an implementation detail — callers
 * see a synchronous facade matching the previous {@code AiFrameworkAdapter} contract. In-process
 * observability hooks attach via {@link ChatStreamListener}; the public surface is blocking.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public interface ChatClient {

  ChatClientResult chat(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory runtimeMemory,
      ChatStreamListener listener);
}
