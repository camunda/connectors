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

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.ActivityBuilder;
import io.camunda.connector.api.inbound.ActivityLogTag;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorManagementContext;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogEntry;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogWriter;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivitySource;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.InvalidInboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.ConnectorNotRegistered;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.FailedToActivate;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.InvalidDefinition;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Activates and deactivates inbound connectors. Designed to be invoked from a single lane thread
 * per process — this class itself holds no locks and returns no futures.
 */
public class BatchExecutableProcessor {

  /**
   * Default upper bound on how long a single {@code executable.activate(...)} or {@code
   * executable.deactivate()} call may run before the runtime gives up waiting.
   */
  public static final Duration DEFAULT_EXECUTABLE_TIMEOUT = Duration.ofMinutes(1);

  private static final Logger LOG = LoggerFactory.getLogger(BatchExecutableProcessor.class);
  private final InboundConnectorFactory connectorFactory;
  private final InboundConnectorContextFactory connectorContextFactory;
  private final ConnectorsInboundMetrics connectorsInboundMetrics;
  private final WebhookConnectorRegistry webhookConnectorRegistry;
  private final ActivityLogWriter activityLogWriter;
  private final Duration executableTimeout;

  public BatchExecutableProcessor(
      InboundConnectorFactory connectorFactory,
      InboundConnectorContextFactory connectorContextFactory,
      ConnectorsInboundMetrics connectorsInboundMetrics,
      @Autowired(required = false) WebhookConnectorRegistry webhookConnectorRegistry,
      ActivityLogWriter activityLogWriter) {
    this(
        connectorFactory,
        connectorContextFactory,
        connectorsInboundMetrics,
        webhookConnectorRegistry,
        activityLogWriter,
        DEFAULT_EXECUTABLE_TIMEOUT);
  }

  public BatchExecutableProcessor(
      InboundConnectorFactory connectorFactory,
      InboundConnectorContextFactory connectorContextFactory,
      ConnectorsInboundMetrics connectorsInboundMetrics,
      WebhookConnectorRegistry webhookConnectorRegistry,
      ActivityLogWriter activityLogWriter,
      Duration executableTimeout) {
    this.connectorFactory = connectorFactory;
    this.connectorContextFactory = connectorContextFactory;
    this.connectorsInboundMetrics = connectorsInboundMetrics;
    this.webhookConnectorRegistry = webhookConnectorRegistry;
    this.activityLogWriter = activityLogWriter;
    this.executableTimeout = executableTimeout;
  }

  /**
   * Activates a batch of inbound connectors. Guarantees all-or-nothing semantics for the batch
   * (except connectors not registered locally, which are considered valid since another runtime may
   * own them — hybrid mode).
   */
  public Map<ExecutableId, RegisteredExecutable> activateBatch(
      Map<ExecutableId, InboundConnectorDetails> request) {

    final Map<ExecutableId, RegisteredExecutable> alreadyActivated = new HashMap<>();

    for (var entry : request.entrySet()) {
      final ExecutableId id = entry.getKey();
      final InboundConnectorDetails maybeValidData = entry.getValue();
      final ValidInboundConnectorDetails data;

      if (maybeValidData instanceof InvalidInboundConnectorDetails invalid) {
        alreadyActivated.put(
            id,
            new RegisteredExecutable.InvalidDefinition(invalid, invalid.error().getMessage(), id));
        continue;
      } else {
        data = (ValidInboundConnectorDetails) maybeValidData;
      }

      final RegisteredExecutable result = activateSingle(data);

      switch (result) {
        case Activated activated -> alreadyActivated.put(id, activated);
        case ConnectorNotRegistered notRegistered -> alreadyActivated.put(id, notRegistered);
        case InvalidDefinition invalid -> alreadyActivated.put(id, invalid);
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
                  new FailedToActivate(failedEntry.getValue(), failureReasonForOthers, id));
            }
          }
          notActivated.put(id, failed);
          return notActivated;
        }
      }
    }
    return alreadyActivated;
  }

  private RegisteredExecutable activateSingle(InboundConnectorDetails data) {

    final ExecutableId id = ExecutableId.fromDeduplicationId(data.deduplicationId());
    if (data instanceof InvalidInboundConnectorDetails invalid) {
      return new InvalidDefinition(invalid, invalid.error().getMessage(), id);
    }
    var validData = (ValidInboundConnectorDetails) data;

    final InboundConnectorExecutable<InboundConnectorContext> executable;
    final InboundConnectorManagementContext context;

    try {
      executable = connectorFactory.getInstance(data.type());
      context =
          (InboundConnectorManagementContext)
              connectorContextFactory.createContext(
                  validData, executable.getClass(), activityLogWriter);
    } catch (NoSuchElementException e) {
      LOG.warn("Failed to create executable", e);
      return new ConnectorNotRegistered(validData, id);
    }

    if (webhookConnectorRegistry == null && executable instanceof WebhookConnectorExecutable) {
      LOG.error("Webhook connector is not supported in this environment");
      context.reportHealth(
          Health.down(
              new UnsupportedOperationException(
                  "Webhook connectors are not supported in this environment")));
      return new ConnectorNotRegistered(validData, id);
    }
    final Activated activated;
    try {
      activated = doActivate(executable, context, id);
    } catch (Exception e) {
      LOG.error("Failed to activate connector", e);
      connectorsInboundMetrics.increaseActivationFailure(data.connectorElements().getFirst());
      return new FailedToActivate(data, e.getMessage(), id);
    }
    log(
        id,
        Activity.newBuilder()
            .withSeverity(Severity.INFO)
            .withTag(ActivityLogTag.LIFECYCLE)
            .withMessage(
                String.format(
                    "Activated inbound connector %s with deduplication ID '%s'",
                    data.type(), data.deduplicationId())));
    connectorsInboundMetrics.increaseActivation(data.connectorElements().getFirst());
    return activated;
  }

  /**
   * Activates the executable and (for webhook connectors) registers it. For webhook connectors we
   * register first to claim the context slot, but roll back the registration if {@code activate}
   * subsequently throws — otherwise the webhook lookup would point at a connector that never
   * started.
   */
  private Activated doActivate(
      InboundConnectorExecutable<InboundConnectorContext> executable,
      InboundConnectorManagementContext context,
      ExecutableId id)
      throws Exception {
    var activated = new Activated(executable, context, id);
    if (executable instanceof WebhookConnectorExecutable) {
      LOG.debug("Registering webhook: {}", context.getDefinition().type());
      boolean acceptedAsActive = webhookConnectorRegistry.register(activated);
      if (acceptedAsActive) {
        try {
          runWithTimeout("Connector activation", () -> executable.activate(context));
        } catch (Exception e) {
          try {
            webhookConnectorRegistry.deregister(activated);
          } catch (Exception deregErr) {
            LOG.warn(
                "Failed to deregister webhook after activation failure for '{}'", id, deregErr);
          }
          throw e;
        }
      }
    } else {
      runWithTimeout("Connector activation", () -> executable.activate(context));
    }
    return activated;
  }

  /**
   * Runs a single user-code call ({@code activate} or {@code deactivate}) on a separate virtual
   * thread and waits up to {@link #executableTimeout} for it to complete. On timeout, interrupts
   * the thread (best-effort) and throws a runtime exception with {@code label} so the caller can
   * record the appropriate state. The lane thread itself just blocks on {@link
   * Thread#join(Duration)} for the bounded duration.
   *
   * <p>Limitation: a connector that catches/ignores {@link InterruptedException} or runs in a
   * CPU-bound loop without checking the interrupt flag may keep running after the timeout. The
   * runtime stops tracking it but cannot force-stop the thread. Resource cleanup is the connector's
   * responsibility.
   */
  private void runWithTimeout(String label, ThrowingRunnable task) throws Exception {
    var failure = new AtomicReference<Throwable>();
    var thread =
        Thread.startVirtualThread(
            () -> {
              try {
                task.run();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    if (!thread.join(executableTimeout)) {
      thread.interrupt();
      throw new RuntimeException(label + " did not complete within " + executableTimeout);
    }
    var t = failure.get();
    if (t == null) return;
    if (t instanceof Exception e) throw e;
    if (t instanceof Error err) throw err;
    throw new RuntimeException(t);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  /** Deactivates a single inbound connector. */
  public void deactivateSingle(RegisteredExecutable executable) {
    if (executable instanceof Activated activated) {
      try {
        if (activated.executable() instanceof WebhookConnectorExecutable) {
          LOG.debug("Unregistering webhook: {}", activated.context().getDefinition().type());
          webhookConnectorRegistry.deregister(activated);
        }
        runWithTimeout("Connector deactivation", () -> activated.executable().deactivate());
        log(
            executable.id(),
            Activity.newBuilder()
                .withSeverity(Severity.INFO)
                .withTag(ActivityLogTag.LIFECYCLE)
                .withMessage(
                    "Deactivated executable: "
                        + activated.context().getDefinition().type()
                        + " with executable ID "
                        + activated.id()));
      } catch (Exception e) {
        LOG.error("Failed to deactivate executable", e);
      }
      connectorsInboundMetrics.increaseDeactivation(
          activated.context().connectorElements().getFirst());
    }
  }

  /** Deactivates a batch of inbound connectors. */
  public void deactivateBatch(List<RegisteredExecutable> executables) {
    for (var executable : executables) {
      deactivateSingle(executable);
    }
  }

  private void log(ExecutableId id, ActivityBuilder activityBuilder) {
    final var activityLogEntry =
        new ActivityLogEntry(id, ActivitySource.RUNTIME, activityBuilder.build());
    activityLogWriter.log(activityLogEntry);
  }
}
