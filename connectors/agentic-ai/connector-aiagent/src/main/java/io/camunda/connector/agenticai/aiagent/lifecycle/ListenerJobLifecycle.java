/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobObserver;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Calls the observers of all listeners in registration order, isolating each failure. */
class ListenerJobLifecycle implements AgentJobLifecycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListenerJobLifecycle.class);

  record Entry(String listenerId, AgentJobObserver observer) {}

  private final List<Entry> entries;
  private final int maxStateSize;

  ListenerJobLifecycle(List<Entry> entries, int maxStateSize) {
    this.entries = entries;
    this.maxStateSize = maxStateSize;
  }

  @Override
  public void jobDeferred(JobDeferred event) {
    notifyAll(
        "job deferred",
        observer -> {
          observer.onJobDeferred(event);
        });
  }

  @Override
  public AgentContext toolDiscoveryDispatched(
      AgentContext agentContext, ToolDiscoveryDispatched event) {
    return contribute(agentContext, "tool discovery", o -> o.onToolDiscoveryDispatched(event));
  }

  @Override
  public void turnStarted(TurnStarted event) {
    notifyAll("turn started", observer -> observer.onTurnStarted(event));
  }

  @Override
  public void modelRoundStarted(ModelRoundStarted event) {
    notifyAll("model round started", observer -> observer.onModelRoundStarted(event));
  }

  @Override
  public void modelRoundCompleted(ModelRoundCompleted event) {
    notifyAll("model round completed", observer -> observer.onModelRoundCompleted(event));
  }

  @Override
  public void modelRoundFailed(ModelRoundFailed event) {
    notifyAll("model round failed", observer -> observer.onModelRoundFailed(event));
  }

  @Override
  public AgentContext turnCompleted(AgentContext agentContext, TurnCompleted event) {
    return contribute(agentContext, "turn completed", o -> o.onTurnCompleted(event));
  }

  @Override
  public void jobFailed(JobFailed event) {
    notifyAll("job failed", observer -> observer.onJobFailed(event));
  }

  @Override
  public void jobCompleted() {
    notifyAll("job completed", AgentJobObserver::onJobCompleted);
  }

  @Override
  public void jobCompletionFailed(JobCompletionFailure failure) {
    notifyAll("job completion failed", observer -> observer.onJobCompletionFailed(failure));
  }

  private void notifyAll(String callback, java.util.function.Consumer<AgentJobObserver> action) {
    for (final var entry : entries) {
      try {
        action.accept(entry.observer());
      } catch (RuntimeException e) {
        LOGGER.warn("Agent lifecycle listener '{}' failed on {}", entry.listenerId(), callback, e);
      }
    }
  }

  private AgentContext contribute(
      AgentContext agentContext,
      String callback,
      Function<AgentJobObserver, @Nullable Map<String, Object>> action) {
    var result = agentContext;
    for (final var entry : entries) {
      try {
        final var state = action.apply(entry.observer());
        if (state == null) {
          continue;
        }
        if (state.toString().length() > maxStateSize) {
          LOGGER.warn(
              "Agent lifecycle listener '{}' state exceeds {} characters; dropping it",
              entry.listenerId(),
              maxStateSize);
          continue;
        }
        result =
            result.withProperty(
                AgentLifecycleDispatcherImpl.STATE_KEY_PREFIX + entry.listenerId(), state);
      } catch (RuntimeException e) {
        LOGGER.warn("Agent lifecycle listener '{}' failed on {}", entry.listenerId(), callback, e);
      }
    }
    return result;
  }
}
