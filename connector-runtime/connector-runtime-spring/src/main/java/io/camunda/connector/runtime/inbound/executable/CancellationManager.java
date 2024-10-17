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

import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.inbound.Health;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles exceptions thrown during the cancellation process of an {@link
 * io.camunda.connector.api.inbound.InboundConnectorContext#cancel(Throwable) cancel} operation.
 * This class determines the appropriate action to take based on the specific exception encountered.
 */
public class CancellationManager {

  private static final Logger LOG = LoggerFactory.getLogger(CancellationManager.class);
  private final Map<UUID, RegisteredExecutable> executables;
  private final ScheduledExecutorService reactivationExecutor;

  public CancellationManager(Map<UUID, RegisteredExecutable> executables) {
    this.executables = executables;
    this.reactivationExecutor = Executors.newSingleThreadScheduledExecutor();
  }

  public static CancellationManager create(Map<UUID, RegisteredExecutable> executables) {
    return new CancellationManager(executables);
  }

  public Consumer<Throwable> createCancellationCallback(UUID uuid) {
    return (throwable) -> {
      switch (throwable) {
        case ConnectorRetryException connectorRetryException -> {
          handleCancellation(uuid, throwable);
          handleRestart(uuid, connectorRetryException);
        }
        default -> handleCancellation(uuid, throwable);
      }
    };
  }

  private void handleRestart(UUID uuid, ConnectorRetryException connectorRetryException) {
    LOG.warn(
        "Inbound connector executable has requested its reactivation in {} {}",
        connectorRetryException.getBackoffDuration().getSeconds(),
        TimeUnit.SECONDS);
    var toReactivate = executables.get(uuid);
    if (toReactivate == null) {
      LOG.error("Inbound connector executable not found for reactivation, received ID: {}", uuid);
      return;
    }
    this.reactivationExecutor.schedule(
        () ->
            tryRestart(
                uuid,
                toReactivate,
                connectorRetryException.getBackoffDuration(),
                connectorRetryException.getRetries()),
        connectorRetryException.getBackoffDuration().getSeconds(),
        TimeUnit.SECONDS);
  }

  private void tryRestart(
      UUID uuid, RegisteredExecutable toReactivate, Duration delay, Integer retryAttempts) {
    if (toReactivate instanceof RegisteredExecutable.Cancelled cancelled) {
      try {
        cancelled.executable().activate(cancelled.context());
        executables.replace(
            uuid, new RegisteredExecutable.Activated(cancelled.executable(), cancelled.context()));
        LOG.info("Activation successful for ID: {}", uuid);
      } catch (Exception e) {
        if (retryAttempts == 0) return;
        LOG.error(
            "Activation failed for ID: {}. Retrying... {} retries left", uuid, retryAttempts - 1);
        this.reactivationExecutor.schedule(
            () -> tryRestart(uuid, toReactivate, delay, retryAttempts - 1),
            delay.getSeconds(),
            TimeUnit.SECONDS);
      }
    } else {
      LOG.error("Executable is not in a cancelled state. Cannot reactivate, ID: {}", uuid);
    }
  }

  private void handleCancellation(UUID uuid, Throwable throwable) {
    LOG.warn("Inbound connector executable has requested its cancellation");
    var toCancel = executables.get(uuid);
    if (toCancel == null) {
      LOG.error("Inbound connector executable not found for the given ID: {}", uuid);
      return;
    }
    if (toCancel instanceof RegisteredExecutable.Activated activated) {
      activated.context().reportHealth(Health.down(throwable));
      try {
        activated.executable().deactivate();
        executables.replace(
            uuid,
            new RegisteredExecutable.Cancelled(
                activated.executable(), activated.context(), throwable));
      } catch (Exception e) {
        LOG.error("Failed to deactivate connector", e);
      }
    } else {
      LOG.error(
          "Attempted to cancel an inbound connector executable that is not in the active state: {}",
          uuid);
    }
  }
}
