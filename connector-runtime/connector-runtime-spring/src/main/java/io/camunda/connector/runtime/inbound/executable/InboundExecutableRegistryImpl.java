/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.inbound.executable.lifecycle.LaneDispatcher;
import io.camunda.connector.runtime.inbound.executable.lifecycle.LifecycleExecutor;
import io.camunda.connector.runtime.inbound.executable.lifecycle.ProcessKey;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin facade over the inbound executable lifecycle. All mutations route through the {@link
 * LaneDispatcher} so work for a single process executes serially on a virtual-thread-backed lane.
 * The lifecycle logic lives in {@link LifecycleExecutor}.
 */
public class InboundExecutableRegistryImpl implements InboundExecutableRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(InboundExecutableRegistryImpl.class);

  private final InboundExecutableStateStore stateStore;
  private final InboundExecutableQueryService queryService;
  private final LaneDispatcher dispatcher;
  private final LifecycleExecutor lifecycle;
  private final Duration resetTimeout;

  public InboundExecutableRegistryImpl(
      InboundConnectorFactory connectorFactory,
      BatchExecutableProcessor batchExecutableProcessor,
      ActivityLogRegistry activityLogRegistry,
      LaneDispatcher dispatcher,
      Duration resetTimeout) {
    this.stateStore = new InMemoryInboundExecutableStateStore();
    var deduplicationScopesByType =
        connectorFactory.getActiveConfigurations().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    InboundConnectorConfiguration::type,
                    InboundConnectorConfiguration::deduplicationProperties,
                    (a, b) -> a));
    var stateTransitionService =
        new InboundExecutableStateTransitionService(deduplicationScopesByType, stateStore);
    this.queryService =
        new InboundExecutableQueryService(stateStore, connectorFactory, activityLogRegistry);
    this.lifecycle =
        new LifecycleExecutor(stateStore, stateTransitionService, batchExecutableProcessor);
    this.dispatcher = dispatcher;
    this.resetTimeout = resetTimeout;
  }

  @Override
  public void publishEvent(InboundExecutableEvent event) {
    if (event instanceof InboundExecutableEvent.ProcessStateChanged stateChanged) {
      LOG.debug("Routing event to lane: {}", event);
      dispatcher.submit(
          ProcessKey.of(stateChanged), () -> lifecycle.applyProcessStateChange(stateChanged));
      return;
    }
    throw new IllegalArgumentException("Unsupported event type: " + event.getClass());
  }

  /**
   * Synchronous test seam. Submits the event to the appropriate lane and blocks on completion.
   * Production callers use {@link #publishEvent}.
   */
  void handleEvent(InboundExecutableEvent.ProcessStateChanged event) {
    var future =
        dispatcher.submit(ProcessKey.of(event), () -> lifecycle.applyProcessStateChange(event));
    awaitFuture(future, resetTimeout);
  }

  @Override
  public List<ActiveExecutableResponse> query(Consumer<ActiveExecutableQuery> filter) {
    var query = new ActiveExecutableQuery();
    Optional.ofNullable(filter).ifPresent(f -> f.accept(query));
    return queryService.query(query);
  }

  @Override
  public String getConnectorName(String type) {
    return queryService.getConnectorName(type);
  }

  @Override
  public RegisteredExecutable reset(ExecutableId id) {
    var current = stateStore.get(id);
    if (current == null) {
      throw new InboundExecutableNotFoundException(id);
    }
    var key = ProcessKey.of(current);
    var future = dispatcher.submit(key, () -> lifecycle.reset(id));
    awaitFuture(future, resetTimeout);
    return stateStore.get(id);
  }

  private void awaitFuture(Future<?> future, Duration timeout) {
    try {
      future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new InboundLifecycleTimeoutException(
          "Inbound lifecycle task did not complete within " + timeout);
    } catch (ExecutionException e) {
      var cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw new RuntimeException(cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting for lifecycle task", e);
    }
  }

  /** Thrown when a synchronous lifecycle wait exceeds its configured timeout. */
  public static class InboundLifecycleTimeoutException extends RuntimeException {
    public InboundLifecycleTimeoutException(String message) {
      super(message);
    }
  }
}
