/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobDeferred;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundCompleted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolDiscoveryDispatched;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnCompleted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnStarted;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.outbound.JobCompletionFailure;

/**
 * The callbacks of one job towards all registered listeners. Every method isolates listener
 * failures; the methods returning an {@link AgentContext} add the state contributed by the
 * listeners to it and otherwise return it unchanged.
 */
public interface AgentJobLifecycle {

  /** Lifecycle of a job without any registered listener; does nothing. */
  AgentJobLifecycle NOOP =
      new AgentJobLifecycle() {
        @Override
        public void jobDeferred(JobDeferred event) {}

        @Override
        public AgentContext toolDiscoveryDispatched(
            AgentContext agentContext, ToolDiscoveryDispatched event) {
          return agentContext;
        }

        @Override
        public void turnStarted(TurnStarted event) {}

        @Override
        public void modelRoundStarted(ModelRoundStarted event) {}

        @Override
        public void modelRoundCompleted(ModelRoundCompleted event) {}

        @Override
        public void modelRoundFailed(ModelRoundFailed event) {}

        @Override
        public AgentContext turnCompleted(AgentContext agentContext, TurnCompleted event) {
          return agentContext;
        }

        @Override
        public void jobFailed(JobFailed event) {}

        @Override
        public void jobCompleted() {}

        @Override
        public void jobCompletionFailed(JobCompletionFailure failure) {}
      };

  void jobDeferred(JobDeferred event);

  AgentContext toolDiscoveryDispatched(AgentContext agentContext, ToolDiscoveryDispatched event);

  void turnStarted(TurnStarted event);

  void modelRoundStarted(ModelRoundStarted event);

  void modelRoundCompleted(ModelRoundCompleted event);

  void modelRoundFailed(ModelRoundFailed event);

  AgentContext turnCompleted(AgentContext agentContext, TurnCompleted event);

  void jobFailed(JobFailed event);

  void jobCompleted();

  void jobCompletionFailed(JobCompletionFailure failure);
}
