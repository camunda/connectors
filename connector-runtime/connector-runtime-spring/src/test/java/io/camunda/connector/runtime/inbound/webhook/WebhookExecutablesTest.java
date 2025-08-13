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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import org.junit.jupiter.api.Test;

public class WebhookExecutablesTest extends WebhookTestsBase {

  private static final String MY_PATH = "myPath";

  @Test
  void shouldAddExecutableToQueue_whenPathIsUsed() {
    // given
    RegisteredExecutable.Activated activeExecutable = buildConnector("processA", 1, MY_PATH);
    WebhookExecutables executables = new WebhookExecutables(activeExecutable, MY_PATH);

    // when
    executables.markAsDownAndAdd(buildConnector("processB", 1, MY_PATH));

    // then
    assertThat(executables.getActiveWebhook()).isEqualTo(activeExecutable);
    assertThat(executables.getInactiveExecutables()).hasSize(1);
    var downExecutable = executables.getInactiveExecutables().getFirst();
    assertThat(downExecutable.context().getHealth())
        .isEqualTo(
            Health.down(
                new IllegalStateException(
                    "Context: myPath already in use by: process processA(testElement)")));
  }

  @Test
  void shouldActivateNextExecutable_whenActiveExecutableIsDeregistered() {
    // given
    RegisteredExecutable.Activated activeExecutable = buildConnector("processA", 1, MY_PATH);
    WebhookExecutables executables = new WebhookExecutables(activeExecutable, MY_PATH);
    RegisteredExecutable.Activated processB = buildConnector("processB", 1, MY_PATH);
    executables.markAsDownAndAdd(processB);
    assertThat(processB.context().getHealth())
        .isEqualTo(
            Health.down(
                new IllegalStateException(
                    "Context: myPath already in use by: process processA(testElement)")));

    // when
    var deleted = executables.deregister(activeExecutable);

    // then
    assertThat(deleted).isTrue();
    assertThat(executables.getActiveWebhook()).isNotNull();
    assertThat(executables.getActiveWebhook()).isEqualTo(processB);
    assertThat(executables.getActiveWebhook().context().getHealth()).isEqualTo(Health.up());
    assertThat(executables.getInactiveExecutables()).isEmpty();
  }

  @Test
  void shouldNotUpdateActiveExecutable_whenInactiveExecutableIsDeregistrered() {
    // given
    RegisteredExecutable.Activated activeExecutable = buildConnector("processA", 1, MY_PATH);
    WebhookExecutables executables = new WebhookExecutables(activeExecutable, MY_PATH);
    RegisteredExecutable.Activated processB = buildConnector("processB", 1, MY_PATH);
    executables.markAsDownAndAdd(processB);
    assertThat(processB.context().getHealth())
        .isEqualTo(
            Health.down(
                new IllegalStateException(
                    "Context: myPath already in use by: process processA(testElement)")));
    assertThat(executables.getActiveWebhook()).isEqualTo(activeExecutable);
    assertThat(executables.getInactiveExecutables()).hasSize(1);
    assertThat(executables.getInactiveExecutables().getFirst()).isEqualTo(processB);

    // when
    boolean hasActiveExecutable = executables.deregister(processB);

    // then
    assertThat(hasActiveExecutable).isTrue();
    assertThat(executables.getActiveWebhook()).isEqualTo(activeExecutable);
    assertThat(executables.getInactiveExecutables()).isEmpty();
    assertThat(activeExecutable.context().getHealth()).isEqualTo(Health.up());
  }

  @Test
  void shouldUpdateHealthStatusMessages_whenNewActiveExecutable() {
    // given
    RegisteredExecutable.Activated activeExecutable = buildConnector("processA", 1, MY_PATH);
    WebhookExecutables executables = new WebhookExecutables(activeExecutable, MY_PATH);
    RegisteredExecutable.Activated processB = buildConnector("processB", 1, MY_PATH);
    RegisteredExecutable.Activated processC = buildConnector("processC", 1, MY_PATH);
    executables.markAsDownAndAdd(processB);
    executables.markAsDownAndAdd(processC);
    assertThat(processB.context().getHealth())
        .isEqualTo(
            Health.down(
                new IllegalStateException(
                    "Context: myPath already in use by: process processA(testElement)")));
    assertThat(processC.context().getHealth())
        .isEqualTo(
            Health.down(
                new IllegalStateException(
                    "Context: myPath already in use by: process processA(testElement)")));

    // when
    executables.deregister(activeExecutable);

    // then
    assertThat(executables.getActiveWebhook()).isEqualTo(processB);
    assertThat(activeExecutable.context().getHealth()).isEqualTo(Health.up());
    assertThat(processC.context().getHealth())
        .isEqualTo(
            Health.down(
                new IllegalStateException(
                    "Context: myPath already in use by: process processB(testElement)")));
  }

  @Test
  void shouldDoNothing_whenTryActivateNextOnEmptyQueue() {
    // given
    RegisteredExecutable.Activated activeExecutable = buildConnector("processA", 1, MY_PATH);
    WebhookExecutables executables = new WebhookExecutables(activeExecutable, MY_PATH);

    // when
    boolean activated = executables.deregister(activeExecutable);

    // then
    assertThat(activated).isFalse();
    var message = assertThrows(IllegalStateException.class, executables::getActiveWebhook);
    assertThat(message.getMessage())
        .isEqualTo("No active (health status = UP) webhooks found for the context: myPath");
    assertThat(executables.getInactiveExecutables()).isEmpty();
  }

  @Test
  void shouldThrowException_whenDeregisteringNonExistentExecutable() {
    // given
    RegisteredExecutable.Activated activeExecutable = buildConnector("processA", 1, MY_PATH);
    WebhookExecutables executables = new WebhookExecutables(activeExecutable, MY_PATH);
    RegisteredExecutable.Activated nonExistentExecutable = buildConnector("processB", 1, MY_PATH);
    // when
    var exception =
        assertThrows(RuntimeException.class, () -> executables.deregister(nonExistentExecutable));
    // then
    assertThat(exception.getMessage())
        .isEqualTo(
            "Cannot deregister executable with definition: "
                + nonExistentExecutable.context().getDefinition()
                + " as it is not registered for context: myPath");
  }

  @Test
  void shouldThrowException_whenDeregisteringAndNoActiveNorQueuedExecutables() {
    // given
    WebhookExecutables executables = new WebhookExecutables(null, MY_PATH);
    // when
    var exception = assertThrows(IllegalStateException.class, executables::getActiveWebhook);
    // then
    assertThat(exception.getMessage())
        .isEqualTo("No active (health status = UP) webhooks found for the context: myPath");
  }
}
