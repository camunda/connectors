/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The callbacks of one job towards all registered listeners, called in registration order.
 *
 * <p>Events are passed as suppliers and built only when at least one listener exists, so a job
 * without listeners does no work for them. A failing supplier or a failing listener never reaches
 * the caller: it is logged and the callback is skipped, for that event or that listener. The
 * methods returning an {@link AgentContext} add the valid state contributed by the listeners to it
 * and otherwise return it unchanged.
 */
public final class AgentJobLifecycle {

  /** Lifecycle of a job without any registered listener; does nothing. */
  public static final AgentJobLifecycle NOOP = new AgentJobLifecycle(List.of(), null);

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentJobLifecycle.class);

  record Entry(String listenerId, AgentJobObserver observer) {}

  private final List<Entry> entries;
  private final @Nullable ListenerStateGuard stateGuard;

  AgentJobLifecycle(List<Entry> entries, ObjectMapper objectMapper, int maxStateBytes) {
    this(entries, new ListenerStateGuard(objectMapper, maxStateBytes));
  }

  private AgentJobLifecycle(List<Entry> entries, @Nullable ListenerStateGuard stateGuard) {
    this.entries = entries;
    this.stateGuard = stateGuard;
  }

  public void jobDeferred(Supplier<JobDeferred> event) {
    notifyAll("job deferred", event, AgentJobObserver::onJobDeferred);
  }

  public AgentContext toolDiscoveryDispatched(
      AgentContext agentContext, Supplier<ToolDiscoveryDispatched> event) {
    return contribute(
        agentContext,
        "tool discovery dispatched",
        event,
        AgentJobObserver::onToolDiscoveryDispatched);
  }

  public void turnStarted(Supplier<TurnStarted> event) {
    notifyAll("turn started", event, AgentJobObserver::onTurnStarted);
  }

  public void modelRoundStarted(Supplier<ModelRoundStarted> event) {
    notifyAll("model round started", event, AgentJobObserver::onModelRoundStarted);
  }

  public void modelRoundCompleted(Supplier<ModelRoundCompleted> event) {
    notifyAll("model round completed", event, AgentJobObserver::onModelRoundCompleted);
  }

  public void modelRoundFailed(Supplier<ModelRoundFailed> event) {
    notifyAll("model round failed", event, AgentJobObserver::onModelRoundFailed);
  }

  public AgentContext turnCompleted(AgentContext agentContext, Supplier<TurnCompleted> event) {
    return contribute(agentContext, "turn completed", event, AgentJobObserver::onTurnCompleted);
  }

  public void jobFailed(Supplier<JobFailed> event) {
    notifyAll("job failed", event, AgentJobObserver::onJobFailed);
  }

  public void jobCompleted() {
    notifyAll("job completed", AgentJobObserver::onJobCompleted);
  }

  public void jobCompletionFailed(JobCompletionFailure failure) {
    notifyAll("job completion failed", observer -> observer.onJobCompletionFailed(failure));
  }

  private <E> void notifyAll(
      String callback, Supplier<E> eventSupplier, BiConsumer<AgentJobObserver, E> action) {
    if (entries.isEmpty()) {
      return;
    }
    final E event = buildEvent(callback, eventSupplier);
    if (event == null) {
      return;
    }
    notifyAll(callback, observer -> action.accept(observer, event));
  }

  private void notifyAll(String callback, Consumer<AgentJobObserver> action) {
    for (final var entry : entries) {
      try {
        action.accept(entry.observer());
      } catch (RuntimeException e) {
        LifecycleFailureLog.listenerFailed(LOGGER, entry.listenerId(), callback, e);
      }
    }
  }

  private <E> AgentContext contribute(
      AgentContext agentContext,
      String callback,
      Supplier<E> eventSupplier,
      BiFunction<AgentJobObserver, E, @Nullable Map<String, Object>> action) {
    if (entries.isEmpty() || stateGuard == null) {
      return agentContext;
    }
    final E event = buildEvent(callback, eventSupplier);
    if (event == null) {
      return agentContext;
    }

    var result = agentContext;
    for (final var entry : entries) {
      try {
        final var state = action.apply(entry.observer(), event);
        if (state == null) {
          continue;
        }
        switch (stateGuard.check(state)) {
          case ListenerStateGuard.Verdict.Accepted accepted ->
              result =
                  result.withProperty(
                      AgentLifecycleDispatcher.STATE_KEY_PREFIX + entry.listenerId(),
                      accepted.state());
          case ListenerStateGuard.Verdict.Rejected rejected ->
              LOGGER.warn(
                  "Agent lifecycle listener '{}' state dropped on {}: {}",
                  entry.listenerId(),
                  callback,
                  rejected.reason().label());
        }
      } catch (RuntimeException e) {
        LifecycleFailureLog.listenerFailed(LOGGER, entry.listenerId(), callback, e);
      }
    }
    return result;
  }

  private static <E> @Nullable E buildEvent(String callback, Supplier<E> eventSupplier) {
    try {
      return eventSupplier.get();
    } catch (RuntimeException e) {
      LifecycleFailureLog.eventFailed(LOGGER, callback, e);
      return null;
    }
  }
}
