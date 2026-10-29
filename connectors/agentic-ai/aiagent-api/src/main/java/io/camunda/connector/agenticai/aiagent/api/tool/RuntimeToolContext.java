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
 * A read-only view of the agent as of a call, safe to read from several threads. The framework
 * implements this interface and may add methods in a later release. {@link Experimental}, see
 * {@link RuntimeTool}.
 *
 * <p>The view is not the conversation: the messages of the running job are not part of it. Both
 * contexts still hold the conversation context of the agent, so a tool can read stored messages
 * with a store that keeps them in the agent context, such as the in-process store. Do not rely on
 * it: a later release may remove this access.
 */
@Experimental
public interface RuntimeToolContext {

  /**
   * The configuration, prompts and limits of the invocation. The features of the agent instance are
   * in {@link #agentContext()}.
   */
  AgentExecutionContext executionContext();

  /**
   * The persisted state of the agent, including its {@link AgentContext#features() features} and
   * {@link AgentContext#properties() properties}, but not the metrics and iteration bookkeeping of
   * the running job. It does not change while the call runs.
   */
  AgentContext agentContext();
}
