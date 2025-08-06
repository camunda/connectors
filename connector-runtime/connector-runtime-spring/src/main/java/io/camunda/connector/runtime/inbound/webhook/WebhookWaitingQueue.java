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

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class WebhookWaitingQueue {
  private static final int MAX_QUEUE_SIZE = 10;

  private static final String QUEUE_FULL_ERROR_MESSAGE =
      "Webhook path \"%s\" queue is full (%d elements). Cannot register connector: %s";

  private static final String TAG_QUEUEING = "Queueing";
  private static final String TAG_ACTIVATION = "Activation";

  private final Map<String, Queue<RegisteredExecutable.Activated>> waitingConnectors =
      new HashMap<>();

  public void markAsDownAndAdd(
      String context,
      RegisteredExecutable.Activated connector,
      RegisteredExecutable.Activated existingExecutable) {
    // Log a fixed Activity message for the connector, Health will be updated later if an element
    // from the queue is activated
    markAsDownAndLogActivity(context, connector, createErrorMessage(context, existingExecutable));

    var added =
        waitingConnectors
            .computeIfAbsent(context, k -> new ArrayBlockingQueue<>(MAX_QUEUE_SIZE, true))
            .offer(connector);

    if (!added) {
      // If the queue is full, throw an exception to indicate that the connector cannot be
      // registered
      // It will be registered by the BatchExecutableProcessor as FailedToActivate
      throw new RuntimeException(
          String.format(
              QUEUE_FULL_ERROR_MESSAGE,
              context,
              MAX_QUEUE_SIZE,
              createErrorMessage(context, existingExecutable)));
    }
  }

  public Optional<RegisteredExecutable.Activated> activateNext(String context) {
    Queue<RegisteredExecutable.Activated> queue = waitingConnectors.get(context);
    if (!hasWaitingConnectors(queue)) {
      return Optional.empty();
    }

    RegisteredExecutable.Activated next = Objects.requireNonNull(queue.poll());
    // "next" is now the active connector for the context
    markAsUp(context, next);
    // Update the queued connectors' health status
    // to indicate that the context is now in use by "next"
    updateWaitingConnectors(context, queue, next);

    return Optional.of(next);
  }

  private void updateWaitingConnectors(
      String context,
      Queue<RegisteredExecutable.Activated> queue,
      RegisteredExecutable.Activated next) {
    if (queue.isEmpty()) {
      waitingConnectors.remove(context);
    } else {
      // update connectors health status using the newly activated connector
      queue.forEach(
          connector ->
              connector
                  .context()
                  .reportHealth(
                      Health.down(new IllegalStateException(createErrorMessage(context, next)))));
    }
  }

  private String createErrorMessage(
      String context, RegisteredExecutable.Activated existingExecutable) {
    Map<String, String> elementIdsByProcessId =
        existingExecutable.context().getDefinition().elements().stream()
            .collect(
                HashMap::new,
                (map, element) -> map.put(element.bpmnProcessId(), element.elementId()),
                HashMap::putAll);
    return "Context: "
        + context
        + " already in use by: "
        + elementIdsByProcessId.entrySet().stream()
            .map(e -> "process " + e.getKey() + "(" + e.getValue() + ")")
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
  }

  private void markAsDownAndLogActivity(
      String context, RegisteredExecutable.Activated connector, String errorMessage) {
    connector
        .context()
        .log(
            Activity.level(Severity.INFO)
                .tag(TAG_QUEUEING)
                .message(
                    "Webhook path \""
                        + context
                        + "\" is already in use. Connector registered in standby and will be activated when the path becomes available."));
    connector.context().reportHealth(Health.down(new IllegalStateException(errorMessage)));
  }

  private void markAsUp(String context, RegisteredExecutable.Activated next) {
    next.context()
        .log(
            Activity.level(Severity.INFO)
                .tag(TAG_ACTIVATION)
                .message(
                    "Path \"" + context + "\" is now available. Connector has been activated."));
    next.context().reportHealth(Health.up());
  }

  private boolean hasWaitingConnectors(Queue<RegisteredExecutable.Activated> queue) {
    return queue != null && !queue.isEmpty();
  }

  public Map<String, Queue<RegisteredExecutable.Activated>> getWaitingConnectors() {
    return waitingConnectors;
  }
}
