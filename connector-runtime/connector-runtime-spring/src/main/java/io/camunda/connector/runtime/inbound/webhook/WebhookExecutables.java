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

public class WebhookExecutables {

  private static final String TAG_QUEUEING = "Queueing";
  private static final String TAG_ACTIVATION = "Activation";

  private final List<RegisteredExecutable.Activated> executables = new ArrayList<>();
  private final String context;

  public WebhookExecutables(RegisteredExecutable.Activated executable, String context) {
    this.executables.add(executable);
    this.context = context;
  }

  List<RegisteredExecutable.Activated> getExecutables() {
    return executables;
  }

  public Optional<RegisteredExecutable.Activated> activateNext() {
    // Remove the active connector from the queue
    executables.removeFirst();

    var firstDownExecutable =
        executables.stream()
            .filter(e -> e.context().getHealth().getStatus() == Health.Status.DOWN)
            .findFirst();

    firstDownExecutable.ifPresent(
        e -> {
          // "next" is now the active connector for the context
          markAsUp(e);
          updateWaitingConnectorsErrorMessage();
        });

    return firstDownExecutable;
  }

  public RegisteredExecutable.Activated getActiveWebhook() {
    var healthyExecutable =
        executables.stream()
            .filter(e -> e.context().getHealth().getStatus() == Health.Status.UP)
            .toList();

    if (healthyExecutable.isEmpty()) {
      throw new IllegalStateException(
          "No active (health status = UP) webhooks found for the context: " + context);
    }
    if (healthyExecutable.size() > 1) {
      throw new IllegalStateException(
          "Multiple active (health status = UP) webhooks found for the same context: " + context);
    }

    return healthyExecutable.getFirst();
  }

  public void markAsDownAndAdd(RegisteredExecutable.Activated connector) {
    markAsDownAndLogActivity(context, connector, createErrorMessage());

    executables.add(connector);
  }

  /**
   * Mark the connector as down and log an activity message. This is used when a connector is
   * registered while the context is already in use.<br>
   * Health will be updated to UP later if this executable gets activated (meaning the context
   * becomes available and this connector is the first in the queue).
   */
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

  private void markAsUp(RegisteredExecutable.Activated next) {
    next.context()
        .log(
            Activity.level(Severity.INFO)
                .tag(TAG_ACTIVATION)
                .message(
                    "Path \"" + context + "\" is now available. Connector has been activated."));
    next.context().reportHealth(Health.up());
  }

  /**
   * Updates the health status of all waiting connectors to DOWN with an error message indicating
   * that the context is already in use by the active connector.
   *
   * @see #getActiveWebhook()
   */
  private void updateWaitingConnectorsErrorMessage() {
    if (executables.isEmpty()) {
      return;
    }

    executables.forEach(
        connector ->
            connector
                .context()
                .reportHealth(Health.down(new IllegalStateException(createErrorMessage()))));
  }

  private String createErrorMessage() {
    Map<String, String> elementIdsByProcessId =
        getActiveWebhook().context().getDefinition().elements().stream()
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
}
