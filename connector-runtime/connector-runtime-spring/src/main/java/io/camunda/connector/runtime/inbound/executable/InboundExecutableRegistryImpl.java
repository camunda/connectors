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
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorReportingContext;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent.Activated;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent.Deactivated;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.metrics.ConnectorMetrics.Inbound;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

public class InboundExecutableRegistryImpl implements InboundExecutableRegistry {

  @Value("${camunda.connector.inbound.log.size:10}")
  private int inboundLogsSize;

  private final InboundConnectorFactory connectorFactory;
  private final InboundConnectorContextFactory connectorContextFactory;
  private final MetricsRecorder metricsRecorder;
  private final WebhookConnectorRegistry webhookConnectorRegistry;

  private final BlockingQueue<InboundExecutableEvent> eventQueue;
  private final ExecutorService executorService;

  private final ConcurrentHashMap<UUID, ActiveExecutable> executables = new ConcurrentHashMap<>();

  private static final Logger LOG = LoggerFactory.getLogger(InboundExecutableRegistryImpl.class);

  public InboundExecutableRegistryImpl(
      InboundConnectorFactory connectorFactory,
      InboundConnectorContextFactory connectorContextFactory,
      MetricsRecorder metricsRecorder,
      @Autowired(required = false) WebhookConnectorRegistry webhookConnectorRegistry) {
    this.connectorFactory = connectorFactory;
    this.connectorContextFactory = connectorContextFactory;
    this.metricsRecorder = metricsRecorder;
    this.webhookConnectorRegistry = webhookConnectorRegistry;
    this.executorService = Executors.newSingleThreadExecutor();
    eventQueue = new LinkedBlockingQueue<>();
    startEventProcessing();
  }

  void startEventProcessing() {
    executorService.submit(
        () -> {
          try {
            while (!Thread.currentThread().isInterrupted()) {
              handleEvent(eventQueue.take()); // blocks until an event is available
            }
          } catch (InterruptedException e) {
            LOG.error("Event processing thread interrupted", e);
          }
        });
  }

  @Override
  public void publishEvent(InboundExecutableEvent event) {
    eventQueue.add(event);
    LOG.debug("Event added to the queue: " + event);
  }

  public void handleEvent(InboundExecutableEvent event) {
    switch (event) {
      case Activated activated -> handleActivated(activated);
      case Deactivated deactivated -> handleDeactivated(deactivated);
    }
  }

  private final BiConsumer<Throwable, UUID> cancellationCallback =
      (throwable, id) -> {
        LOG.warn("Inbound connector executable has requested its cancellation", throwable);
        var toCancel = executables.get(id);
        if (toCancel == null) {
          LOG.error("Inbound connector executable not found for the given ID: " + id);
          return;
        }
        toCancel.context().reportHealth(Health.down(throwable));
        try {
          toCancel.executable().deactivate();
        } catch (Exception e) {
          LOG.error("Failed to deactivate connector", e);
        }
      };

  private void handleActivated(Activated activated) {
    LOG.debug("Handling activated event for connector: " + activated.executableId());
    var definition = activated.definition();
    final InboundConnectorExecutable executable;
    final InboundConnectorReportingContext context;

    try {
      executable = connectorFactory.getInstance(activated.definition().type());
      context =
          (InboundConnectorReportingContext)
              connectorContextFactory.createContext(
                  definition,
                  (throwable) -> cancellationCallback.accept(throwable, activated.executableId()),
                  executable.getClass(),
                  EvictingQueue.create(inboundLogsSize));
    } catch (Exception e) {
      LOG.error("Failed to create executable", e);
      return;
    }

    var result =
        executables.putIfAbsent(
            activated.executableId(), new ActiveExecutable(executable, context));
    if (result != null) {
      throw new IllegalStateException(
          "Executable with ID " + activated.executableId() + " already exists");
    }

    if (webhookConnectorRegistry == null && executable instanceof WebhookConnectorExecutable) {
      LOG.error("Webhook connector is not supported in this environment");
      context.reportHealth(
          Health.down(
              new UnsupportedOperationException(
                  "Webhook connectors are not supported in this environment")));
      return;
    }

    try {
      if (executable instanceof WebhookConnectorExecutable) {
        LOG.debug("Registering webhook: " + definition.type());
        webhookConnectorRegistry.register(new ActiveExecutable(executable, context));
      }
      executable.activate(context);
    } catch (Exception e) {
      LOG.error("Failed to activate connector", e);
      context.reportHealth(Health.down(e));
    }

    LOG.info(
        "Inbound connector {} activated with deduplication ID '{}' and executable ID '{}'",
        definition.type(),
        definition.deduplicationId(),
        activated.executableId());
    metricsRecorder.increase(
        Inbound.METRIC_NAME_ACTIVATIONS, Inbound.ACTION_ACTIVATED, definition.type());
  }

  void handleDeactivated(Deactivated deactivated) {
    LOG.debug("Handling deactivated event for connector: " + deactivated.executableId());
    var activeExecutable = executables.remove(deactivated.executableId());
    if (activeExecutable == null) {
      LOG.error("Executable with ID " + deactivated.executableId() + " not found");
      return;
    }

    try {
      if (activeExecutable.executable() instanceof WebhookConnectorExecutable) {
        LOG.debug("Unregistering webhook: " + activeExecutable.context().getDefinition().type());
        webhookConnectorRegistry.deregister(activeExecutable);
      }
      activeExecutable.executable().deactivate();
    } catch (Exception e) {
      LOG.error("Failed to deactivate executable", e);
    }
    metricsRecorder.increase(
        Inbound.METRIC_NAME_ACTIVATIONS,
        Inbound.ACTION_DEACTIVATED,
        activeExecutable.context().getDefinition().type());
  }

  @Override
  public List<ActiveExecutableResponse> query(ActiveExecutableQuery query) {
    return executables.entrySet().stream()
        .filter(entry -> matchesQuery(entry.getValue(), query))
        .map(entry -> mapToResponse(entry.getKey(), entry.getValue()))
        .toList();
  }

  private boolean matchesQuery(ActiveExecutable executable, ActiveExecutableQuery query) {
    var definition = executable.context().getDefinition();
    var elements = executable.context().getDefinition().elements();
    return elements.stream()
        .anyMatch(
            element ->
                element.bpmnProcessId().equals(query.bpmnProcessId())
                    && definition.type().equals(query.type())
                    && definition.tenantId().equals(query.tenantId())
                    && element.elementId().equals(query.elementId()));
  }

  private ActiveExecutableResponse mapToResponse(UUID id, ActiveExecutable connector) {
    return new ActiveExecutableResponse(
        id,
        connector.executable().getClass(),
        (InboundConnectorDefinitionImpl) connector.context().getDefinition(),
        connector.context().getHealth(),
        connector.context().getLogs());
  }

  // print status report every hour
  @Scheduled(fixedRate = 60 * 60 * 1000)
  public void logStatusReport() {
    LOG.info("Inbound connector status report - {} executables active", executables.size());
    executables.values().stream()
        .collect(
            Collectors.groupingBy(
                activeExecutable -> activeExecutable.context().getDefinition().type(),
                Collectors.counting()))
        .forEach((type, count) -> LOG.info(". '{}' - {}", type, count));
  }
}
