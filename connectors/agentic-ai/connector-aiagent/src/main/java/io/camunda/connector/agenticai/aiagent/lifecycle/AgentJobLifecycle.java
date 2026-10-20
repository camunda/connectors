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
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The callbacks of one job towards all registered listeners, called in registration order. Every
 * method isolates listener failures; the methods returning an {@link AgentContext} add the state
 * contributed by the listeners to it and otherwise return it unchanged.
 */
public final class AgentJobLifecycle {

  /** Lifecycle of a job without any registered listener; does nothing. */
  public static final AgentJobLifecycle NOOP = new AgentJobLifecycle(List.of(), 0);

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentJobLifecycle.class);

  record Entry(String listenerId, AgentJobObserver observer) {}

  private final List<Entry> entries;
  private final int maxStateSize;

  AgentJobLifecycle(List<Entry> entries, int maxStateSize) {
    this.entries = entries;
    this.maxStateSize = maxStateSize;
  }

  public void jobDeferred(JobDeferred event) {
    notifyAll("job deferred", observer -> observer.onJobDeferred(event));
  }

  public AgentContext toolDiscoveryDispatched(
      AgentContext agentContext, ToolDiscoveryDispatched event) {
    return contribute(agentContext, "tool discovery", o -> o.onToolDiscoveryDispatched(event));
  }

  public void turnStarted(TurnStarted event) {
    notifyAll("turn started", observer -> observer.onTurnStarted(event));
  }

  public void modelRoundStarted(ModelRoundStarted event) {
    notifyAll("model round started", observer -> observer.onModelRoundStarted(event));
  }

  public void modelRoundCompleted(ModelRoundCompleted event) {
    notifyAll("model round completed", observer -> observer.onModelRoundCompleted(event));
  }

  public void modelRoundFailed(ModelRoundFailed event) {
    notifyAll("model round failed", observer -> observer.onModelRoundFailed(event));
  }

  public AgentContext turnCompleted(AgentContext agentContext, TurnCompleted event) {
    return contribute(agentContext, "turn completed", o -> o.onTurnCompleted(event));
  }

  public void jobFailed(JobFailed event) {
    notifyAll("job failed", observer -> observer.onJobFailed(event));
  }

  public void jobCompleted() {
    notifyAll("job completed", AgentJobObserver::onJobCompleted);
  }

  public void jobCompletionFailed(JobCompletionFailure failure) {
    notifyAll("job completion failed", observer -> observer.onJobCompletionFailed(failure));
  }

  private void notifyAll(String callback, Consumer<AgentJobObserver> action) {
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
                AgentLifecycleDispatcher.STATE_KEY_PREFIX + entry.listenerId(), state);
      } catch (RuntimeException e) {
        LOGGER.warn("Agent lifecycle listener '{}' failed on {}", entry.listenerId(), callback, e);
      }
    }
    return result;
  }
}
