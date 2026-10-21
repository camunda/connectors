/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A job started and the agent is initialized. A job is one job activation, the unit that Zeebe and
 * Operate show. Carries the listener's own state from the agent context of the previous persisting
 * job.
 *
 * @param startedAt when the job entered the handler
 * @param agentInstanceKey the agent instance of the agent, absent for agents without one
 * @param lastIterationKey the highest turn iteration key persisted so far, if any
 * @param previousState the state this listener contributed earlier, empty when none exists
 */
@Experimental
public record JobStarted(
    AgentJobInfo job,
    AgentFlavor flavor,
    AgentJobKind kind,
    Instant startedAt,
    @Nullable Long agentInstanceKey,
    @Nullable Integer lastIterationKey,
    Map<String, Object> previousState) {}
