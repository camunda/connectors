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

import com.google.common.collect.EvictingQueue;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorReportingContext;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.InvalidInboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.ConnectorNotRegistered;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.FailedToActivate;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.InvalidDefinition;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class BatchExecutableProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(BatchExecutableProcessor.class);
  private final InboundConnectorFactory connectorFactory;
  private final InboundConnectorContextFactory connectorContextFactory;
  private final ConnectorsInboundMetrics connectorsInboundMetrics;
  private final WebhookConnectorRegistry webhookConnectorRegistry;

  @Value("${camunda.connector.inbound.log.size:10}")
  private int inboundLogsSize;

  public BatchExecutableProcessor(
      InboundConnectorFactory connectorFactory,
      InboundConnectorContextFactory connectorContextFactory,
      ConnectorsInboundMetrics connectorsInboundMetrics,
      @Autowired(required = false) WebhookConnectorRegistry webhookConnectorRegistry) {
    this.connectorFactory = connectorFactory;
    this.connectorContextFactory = connectorContextFactory;
    this.connectorsInboundMetrics = connectorsInboundMetrics;
    this.webhookConnectorRegistry = webhookConnectorRegistry;
  }

  /**
   * Activates a batch of inbound connectors. Guarantees that all connectors are activated or none
   * (except non-registered connectors, which can be activated by a different runtime - those are
   * considered valid).
   */
  public Map<ExecutableId, RegisteredExecutable> activateBatch(
      Map<ExecutableId, InboundConnectorDetails> request,
      Consumer<InboundExecutableEvent.Cancelled> cancellationCallback) {

    final Map<ExecutableId, RegisteredExecutable> alreadyActivated = new HashMap<>();

    for (var entry : request.entrySet()) {
      final ExecutableId id = entry.getKey();
      final InboundConnectorDetails maybeValidData = entry.getValue();
      final ValidInboundConnectorDetails data;

      if (maybeValidData instanceof InvalidInboundConnectorDetails invalid) {
        alreadyActivated.put(
            id, new RegisteredExecutable.InvalidDefinition(invalid, invalid.error().getMessage()));
        continue;
      } else {
        data = (ValidInboundConnectorDetails) maybeValidData;
      }

      final RegisteredExecutable result =
          activateSingle(
              data, t -> cancellationCallback.accept(new InboundExecutableEvent.Cancelled(id, t)));

      switch (result) {
        case Activated activated -> alreadyActivated.put(id, activated);
        case ConnectorNotRegistered notRegistered -> alreadyActivated.put(id, notRegistered);
        case InvalidDefinition invalid -> alreadyActivated.put(id, invalid);
        case RegisteredExecutable.Cancelled cancelled -> alreadyActivated.put(id, cancelled);
        case FailedToActivate failed -> {
          LOG.error(
              "Failed to activate connector of type '{}' with deduplication ID '{}', reason: {}. "
                  + "All previously activated executables from this batch will be discarded.",
              failed.data().type(),
              failed.data().deduplicationId(),
              failed.reason());

          // deactivate all previously activated connectors
          deactivateBatch(new ArrayList<>(alreadyActivated.values()));

          var failureReasonForOthers =
              "Process contains invalid connector(s): "
                  + String.join(
                      ", ",
                      failed.data().connectorElements().stream()
                          .map(e -> e.element().elementId())
                          .toList())
                  + ". Reason: "
                  + failed.reason();

          Map<ExecutableId, RegisteredExecutable> notActivated = new HashMap<>();
          for (var failedEntry : request.entrySet()) {
            if (!failedEntry.getKey().equals(id)) {
              notActivated.put(
                  failedEntry.getKey(),
                  new FailedToActivate(failedEntry.getValue(), failureReasonForOthers));
            }
          }
          notActivated.put(id, failed);
          return notActivated;
        }
      }
    }
    return alreadyActivated;
  }

  private RegisteredExecutable activateSingle(
      InboundConnectorDetails data, Consumer<Throwable> cancellationCallback) {

    if (data instanceof InvalidInboundConnectorDetails invalid) {
      return new InvalidDefinition(invalid, invalid.error().getMessage());
    }
    var validData = (ValidInboundConnectorDetails) data;

    final InboundConnectorExecutable<InboundConnectorContext> executable;
    final InboundConnectorReportingContext context;

    try {
      executable = connectorFactory.getInstance(data.type());
      context =
          (InboundConnectorReportingContext)
              connectorContextFactory.createContext(
                  validData,
                  cancellationCallback,
                  executable.getClass(),
                  EvictingQueue.create(inboundLogsSize));
    } catch (NoSuchElementException e) {
      LOG.warn("Failed to create executable", e);
      return new ConnectorNotRegistered(validData);
    }

    if (webhookConnectorRegistry == null && executable instanceof WebhookConnectorExecutable) {
      LOG.error("Webhook connector is not supported in this environment");
      context.reportHealth(
          Health.down(
              new UnsupportedOperationException(
                  "Webhook connectors are not supported in this environment")));
      return new ConnectorNotRegistered(validData);
    }
    try {
      if (executable instanceof WebhookConnectorExecutable) {
        LOG.debug("Registering webhook: {}", data.type());
        webhookConnectorRegistry.register(new RegisteredExecutable.Activated(executable, context));
      }
      executable.activate(context);
    } catch (Exception e) {
      LOG.error("Failed to activate connector", e);
      connectorsInboundMetrics.increaseActivationFailure(data.connectorElements().getFirst());
      return new FailedToActivate(data, e.getMessage());
    }
    LOG.info(
        "Inbound connector {} activated with deduplication ID '{}'",
        data.type(),
        data.deduplicationId());
    connectorsInboundMetrics.increaseActivation(data.connectorElements().getFirst());
    return new Activated(executable, context);
  }

  /** Deactivates a batch of inbound connectors. */
  public void deactivateBatch(List<RegisteredExecutable> executables) {
    for (var activeExecutable : executables) {
      if (activeExecutable instanceof Activated activated) {
        try {
          LOG.info("Deactivating executable: {}", activated.context().getDefinition().type());
          if (activated.executable() instanceof WebhookConnectorExecutable) {
            LOG.debug("Unregistering webhook: {}", activated.context().getDefinition().type());
            webhookConnectorRegistry.deregister(activated);
          }
          activated.executable().deactivate();
        } catch (Exception e) {
          LOG.error("Failed to deactivate executable", e);
        }
        connectorsInboundMetrics.increaseDeactivation(
            activated.context().connectorElements().getFirst());
      }
    }
  }

  public CompletableFuture<Activated> restartFromContext(
      RegisteredExecutable.Cancelled cancelled, ConnectorRetryException retryException) {

    InboundConnectorExecutable<InboundConnectorContext> newExecutable =
        connectorFactory.getInstance(cancelled.context().getDefinition().type());
    LOG.warn("Inbound connector executable has requested its reactivation");
    try {
      RetryPolicy<Object> retryPolicy =
          RetryPolicy.builder()
              .withDelay(retryException.getBackoffDuration())
              .onFailedAttempt(
                  event ->
                      LOG.error(
                          "Reactivation failed for inbound connector: {}",
                          cancelled.context().getDefinition().type(),
                          event.getLastException()))
              .onRetry(
                  event ->
                      LOG.warn(
                          "Failure #{} to reactivate connector: {}. Retrying.",
                          event.getAttemptCount(),
                          cancelled.context().getDefinition().type()))
              .withMaxRetries(retryException.getRetries())
              .build();
      return Failsafe.with(retryPolicy)
          .getAsync(() -> tryRestart(newExecutable, cancelled.context()));
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private Activated tryRestart(
      InboundConnectorExecutable<InboundConnectorContext> executable,
      InboundConnectorReportingContext context) {
    try {
      executable.activate(context);
      LOG.info("Activation successful for {}", context.getDefinition().type());
      return new Activated(executable, context);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public RegisteredExecutable.Cancelled cancelExecutable(Activated activated, Throwable throwable) {
    try {
      activated.executable().deactivate();
      return new RegisteredExecutable.Cancelled(
          activated.executable(), activated.context(), throwable);
    } catch (Exception e) {
      LOG.error("Failed to deactivate connector", e);
      throw new RuntimeException("Failed to deactivate connector", e);
    }
  }
}
