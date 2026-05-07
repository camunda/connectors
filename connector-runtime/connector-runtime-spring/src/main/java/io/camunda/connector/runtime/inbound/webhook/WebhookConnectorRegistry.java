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
package io.camunda.connector.runtime.inbound.webhook;

import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.webhook.model.CommonWebhookProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of active webhook executables, keyed by webhook context (URL path).
 *
 * <p>Concurrency model: every public operation is submitted as a {@link Callable} to a
 * single-thread virtual-thread executor and the caller blocks on the resulting future. Because
 * exactly one thread ever touches the registry's internals, no locks, no concurrent collections,
 * and no synchronisation primitives are needed. The map is a plain {@link HashMap} and {@link
 * WebhookExecutables} is plain mutable Java.
 *
 * <p>Webhook connector activations are expected to be fast (in-memory state setup, no I/O), so the
 * coordinator is never blocked for meaningful durations in normal operation.
 */
public class WebhookConnectorRegistry {

  private final Logger LOG = LoggerFactory.getLogger(WebhookConnectorRegistry.class);

  private final Map<String, WebhookExecutables> executablesByContext = new HashMap<>();
  private final ExecutorService coordinator =
      Executors.newSingleThreadExecutor(
          Thread.ofVirtual().name("inbound-webhook-coordinator").factory());

  public Optional<RegisteredExecutable.Activated> getActiveWebhook(String context) {
    return runOnCoordinator(
        () -> {
          var executables = executablesByContext.get(context);
          if (executables == null) {
            return Optional.empty();
          }
          try {
            return Optional.ofNullable(executables.getActiveWebhook());
          } catch (IllegalStateException ignored) {
            return Optional.empty();
          }
        });
  }

  /**
   * Returns an immutable snapshot of the current registry. Callers may iterate it freely without
   * coordinating with the registry; mutations after the snapshot is taken are not reflected.
   */
  public Map<String, WebhookExecutables> getExecutablesByContext() {
    return runOnCoordinator(() -> Map.copyOf(executablesByContext));
  }

  public boolean register(RegisteredExecutable.Activated connector) {
    var context = getContext(connector);
    WebhookConnectorValidationUtil.logIfWebhookPathDeprecated(connector, context);

    return runOnCoordinator(
        () -> {
          var existing = executablesByContext.get(context);
          if (existing == null) {
            executablesByContext.put(context, new WebhookExecutables(connector, context));
          } else {
            existing.markAsDownAndAdd(connector);
          }
          return registeredAsActiveConnector(connector, context);
        });
  }

  public void deregister(RegisteredExecutable.Activated connector) {
    var context = getContext(connector);
    runOnCoordinator(
        () -> {
          var executables = executablesByContext.get(context);
          if (executables == null) {
            var logMessage = "Context: " + context + " is not registered. Cannot deregister.";
            LOG.debug(logMessage);
            throw new RuntimeException(logMessage);
          }
          var hasActiveConnector = executables.deregister(connector);
          if (!hasActiveConnector) {
            executablesByContext.remove(context);
          }
          return null;
        });
  }

  public void reset() {
    runOnCoordinator(
        () -> {
          executablesByContext.clear();
          return null;
        });
  }

  /** Runs entirely inside the coordinator; no synchronisation needed. */
  private boolean registeredAsActiveConnector(
      RegisteredExecutable.Activated connector, String context) {
    var executables = executablesByContext.get(context);
    if (executables == null) {
      return false;
    }
    try {
      return connector.equals(executables.getActiveWebhook());
    } catch (IllegalStateException ignored) {
      return false;
    }
  }

  private String getContext(RegisteredExecutable.Activated connector) {
    var properties = connector.context().bindProperties(CommonWebhookProperties.class);
    var context = properties.getContext();
    if (context == null) {
      var logMessage = "Webhook path not provided";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
    return context;
  }

  private <T> T runOnCoordinator(Callable<T> task) {
    try {
      return coordinator.submit(task).get();
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
      throw new RuntimeException("Interrupted while waiting for webhook coordinator", e);
    }
  }
}
