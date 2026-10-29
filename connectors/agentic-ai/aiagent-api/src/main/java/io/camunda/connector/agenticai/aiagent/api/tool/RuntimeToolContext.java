/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.common.annotation.Experimental;

/**
 * What a runtime tool can read about the agent while it runs a call. The framework implements this
 * interface: a method may be added in a later release.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The view is read-only and as of the call. It is not the conversation: a tool cannot change
 * messages, and the messages of the running job are not part of it. Both contexts still hold the
 * conversation context of the agent, so a tool can read stored messages with a store that keeps
 * them in the agent context, such as the in-process store. Do not rely on it: a later release may
 * remove this access. It is safe to read from several threads.
 */
@Experimental
public interface RuntimeToolContext {

  /**
   * The invocation of the agent: its configuration, prompts and limits. The features of the agent
   * instance are not part of it; read them from {@link #agentContext()}.
   */
  AgentExecutionContext executionContext();

  /**
   * The persisted state of the agent as of this call, including the {@link AgentContext#features()
   * features} of the agent instance and its {@link AgentContext#properties() properties}. Do not
   * rely on the metrics, which cover the finished turns only. The returned value does not change
   * while the call runs.
   */
  AgentContext agentContext();

  /**
   * The documents the agent knows as of the start of the batch of calls this call belongs to. The
   * documents the other calls of the batch return are not part of it, they are known to the next
   * turn. Empty when a tool is asked whether it is {@link RuntimeTool#isEnabled enabled}.
   */
  RuntimeToolDocuments documents();
}
