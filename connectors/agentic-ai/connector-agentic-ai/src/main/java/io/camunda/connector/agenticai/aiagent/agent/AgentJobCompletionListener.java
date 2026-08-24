/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.api.outbound.JobCompletionFailure;

/**
 * Internal lifecycle callback used to thread post-completion hooks from the agent request handler
 * to the connector function.
 *
 * <p>Implementations capture per-execution state (e.g. conversation store, agent context) at the
 * point the response is built. The connector function delegates to it from its SDK-level {@link
 * io.camunda.connector.api.outbound.JobCompletionListener} once Zeebe has accepted or rejected the
 * job completion command.
 */
public interface AgentJobCompletionListener {

  default void onJobCompleted() {}

  default void onJobCompletionFailed(JobCompletionFailure failure) {}
}
