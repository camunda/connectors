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

  private final List<RegisteredExecutable.Activated> downExecutables = new ArrayList<>();
  private RegisteredExecutable.Activated activeExecutable;
  private final String context;

  public WebhookExecutables(RegisteredExecutable.Activated activeExecutable, String context) {
    this.activeExecutable = activeExecutable;
    this.context = context;
  }

  public List<RegisteredExecutable.Activated> getAllExecutables() {
    List<RegisteredExecutable.Activated> allExecutables = new ArrayList<>(downExecutables);
    if (activeExecutable != null) {
      allExecutables.addFirst(activeExecutable);
    }
    return allExecutables;
  }

  public List<RegisteredExecutable.Activated> getDownExecutables() {
    return downExecutables;
  }

  /**
   * Deregisters an executable for the given context. If the executable is not registered for the
   * context, it throws a RuntimeException.
   *
   * @param executable the executable to deregister
   * @return true if there's an active executable for the context after deregistration, false
   *     otherwise.
   * @throws RuntimeException if the executable is not registered for the context.
   */
  public boolean deregister(RegisteredExecutable.Activated executable) {
    if (activeExecutable == null && downExecutables.isEmpty()) {
      throw new IllegalStateException(
          "No active or queued executables found for the context: " + context);
    }

    if (sameExecutables(activeExecutable, executable)) {
      activeExecutable = null;
      return tryActivateNext();
    }

    var foundExecutable =
        downExecutables.stream()
            .filter(e -> sameExecutables(e, executable))
            .findFirst()
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Cannot deregister executable with definition: "
                            + executable.context().getDefinition()
                            + " as it is not registered for context: "
                            + context));
    downExecutables.remove(foundExecutable);
    return true;
  }

  private boolean sameExecutables(
      RegisteredExecutable.Activated executableA, RegisteredExecutable.Activated executableB) {
    return executableA != null
        && executableB != null
        && executableA.context().getDefinition().equals(executableB.context().getDefinition());
  }

  /**
   * Tries to activate the next executable in the queue. It also updates the health status of the
   * active executable (to UP) and all other executables in the queue (error message).
   *
   * @return true if an executable was activated, false if there are no executables in the queue
   */
  private boolean tryActivateNext() {
    if (downExecutables.isEmpty()) {
      return false;
    }

    activeExecutable = downExecutables.removeFirst();
    markActiveExecutableAsUp();
    updateDownExecutablesErrorMessage();

    return true;
  }

  public RegisteredExecutable.Activated getActiveWebhook() {
    if (activeExecutable == null) {
      throw new IllegalStateException(
          "No active (health status = UP) webhooks found for the context: " + context);
    }
    return activeExecutable;
  }

  public void markAsDownAndAdd(RegisteredExecutable.Activated executable) {
    markAsDownAndLogActivity(context, executable);
    downExecutables.add(executable);
  }

  /**
   * Mark the executable as down and log an activity message. This is used when an executable is
   * registered while the context is already in use.<br>
   * Health will be updated to UP later if this executable gets activated (meaning the context
   * becomes available and this executable is the first in the queue).
   */
  private void markAsDownAndLogActivity(String context, RegisteredExecutable.Activated executable) {
    executable
        .context()
        .log(
            Activity.level(Severity.INFO)
                .tag(TAG_QUEUEING)
                .message(
                    "Webhook path \""
                        + context
                        + "\" is already in use. Executable registered in standby and will be activated when the path becomes available."));
    executable.context().reportHealth(Health.down(new IllegalStateException(createErrorMessage())));
  }

  private void markActiveExecutableAsUp() {
    activeExecutable
        .context()
        .log(
            Activity.level(Severity.INFO)
                .tag(TAG_ACTIVATION)
                .message(
                    "Path \"" + context + "\" is now available. executable has been activated."));
    activeExecutable.context().reportHealth(Health.up());
  }

  /**
   * Updates the health status of all waiting executables to DOWN with an error message indicating
   * that the context is already in use by the active executable.
   *
   * @see #getActiveWebhook()
   */
  private void updateDownExecutablesErrorMessage() {
    if (downExecutables.isEmpty()) {
      return;
    }

    downExecutables.forEach(
        executable ->
            executable
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
