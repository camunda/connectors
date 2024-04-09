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
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorReportingContext;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.ConnectorNotRegistered;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.FailedToActivate;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.metrics.ConnectorMetrics.Inbound;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class BatchExecutableProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(BatchExecutableProcessor.class);

  @Value("${camunda.connector.inbound.log.size:10}")
  private int inboundLogsSize;

  private final InboundConnectorFactory connectorFactory;
  private final InboundConnectorContextFactory connectorContextFactory;
  private final MetricsRecorder metricsRecorder;
  private final WebhookConnectorRegistry webhookConnectorRegistry;

  public BatchExecutableProcessor(
      InboundConnectorFactory connectorFactory,
      InboundConnectorContextFactory connectorContextFactory,
      MetricsRecorder metricsRecorder,
      @Autowired(required = false) WebhookConnectorRegistry webhookConnectorRegistry) {
    this.connectorFactory = connectorFactory;
    this.connectorContextFactory = connectorContextFactory;
    this.metricsRecorder = metricsRecorder;
    this.webhookConnectorRegistry = webhookConnectorRegistry;
  }

  /**
   * Activates a batch of inbound connectors. Guarantees that all connectors are activated or none
   * (except non-registered connectors, which can be activated by a different runtime - those are
   * considered valid).
   */
  public Map<UUID, RegisteredExecutable> activateBatch(
      Map<UUID, InboundConnectorDetails> request,
      BiConsumer<Throwable, UUID> cancellationCallback) {

    final Map<UUID, RegisteredExecutable> alreadyActivated = new HashMap<>();

    for (var entry : request.entrySet()) {
      final UUID id = entry.getKey();
      final InboundConnectorDetails data = entry.getValue();

      final RegisteredExecutable result =
          activateSingle(data, e -> cancellationCallback.accept(e, id));

      switch (result) {
        case Activated activated -> alreadyActivated.put(id, activated);
        case ConnectorNotRegistered notRegistered -> alreadyActivated.put(id, notRegistered);
        case FailedToActivate failed -> {
          LOG.error(
              "Failed to activate connector of type '{}' with deduplication ID '{}', reason: {}. "
                  + "All previously activated executables from this batch will be discarded.",
              failed.data().type(),
              failed.data().deduplicationId(),
              failed.reason());

          // deactivate all previously activated connectors
          deactivateBatch(List.of(failed));

          var failureReasonForOthers =
              "Process contains invalid connector(s): "
                  + String.join(
                      ", ",
                      failed.data().connectorElements().stream()
                          .map(e -> e.element().elementId())
                          .toList())
                  + ". Reason: "
                  + failed.reason();

          Map<UUID, RegisteredExecutable> notActivated = new HashMap<>();
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

    final InboundConnectorExecutable executable;
    final InboundConnectorReportingContext context;

    try {
      executable = connectorFactory.getInstance(data.type());
      context =
          (InboundConnectorReportingContext)
              connectorContextFactory.createContext(
                  data,
                  cancellationCallback,
                  executable.getClass(),
                  EvictingQueue.create(inboundLogsSize));
    } catch (NoSuchElementException e) {
      LOG.error("Failed to create executable", e);
      return new ConnectorNotRegistered(data);
    }

    if (webhookConnectorRegistry == null && executable instanceof WebhookConnectorExecutable) {
      LOG.error("Webhook connector is not supported in this environment");
      context.reportHealth(
          Health.down(
              new UnsupportedOperationException(
                  "Webhook connectors are not supported in this environment")));
      return new ConnectorNotRegistered(data);
    }

    try {
      if (executable instanceof WebhookConnectorExecutable) {
        LOG.debug("Registering webhook: {}", data.type());
        webhookConnectorRegistry.register(new RegisteredExecutable.Activated(executable, context));
      }
      executable.activate(context);
    } catch (Exception e) {
      LOG.error("Failed to activate connector", e);
      return new FailedToActivate(data, e.getMessage());
    }

    LOG.info(
        "Inbound connector {} activated with deduplication ID '{}'",
        data.type(),
        data.deduplicationId());

    metricsRecorder.increase(
        Inbound.METRIC_NAME_ACTIVATIONS, Inbound.ACTION_ACTIVATED, data.type());

    return new Activated(executable, context);
  }

  /** Deactivates a batch of inbound connectors. */
  public void deactivateBatch(List<RegisteredExecutable> executables) {
    for (var activeExecutable : executables) {
      if (activeExecutable instanceof Activated activated) {
        try {
          if (activated.executable() instanceof WebhookConnectorExecutable) {
            LOG.debug("Unregistering webhook: {}", activated.context().getDefinition().type());
            webhookConnectorRegistry.deregister(activated);
          }
          activated.executable().deactivate();
        } catch (Exception e) {
          LOG.error("Failed to deactivate executable", e);
        }
        metricsRecorder.increase(
            Inbound.METRIC_NAME_ACTIVATIONS,
            Inbound.ACTION_DEACTIVATED,
            activated.context().getDefinition().type());
      }
    }
  }
}
