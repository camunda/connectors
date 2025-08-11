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
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import org.junit.jupiter.api.Test;

public class WebhookExecutablesTest extends WebhookTestsBase {

  @Test
  void testAddConnectorToQueue() {
    RegisteredExecutable.Activated activeExecutable = buildConnector("processA", 1, "myPath");
    WebhookExecutables executables = new WebhookExecutables(activeExecutable, "myPath");
    executables.markAsDownAndAdd(buildConnector("processB", 1, "myPath"));

    assertThat(executables.getActiveWebhook()).isEqualTo(activeExecutable);
    assertThat(executables.getDownExecutables()).hasSize(1);
    var downExecutable = executables.getDownExecutables().getFirst();
    assertThat(downExecutable.context().getHealth())
        .isEqualTo(
            Health.down(
                new IllegalStateException(
                    "Context: myPath already in use by: process processA(testElement)")));
  }

  @Test
  void testTryActivateNextConnector() {}

  @Test
  void testHealthStatusUpdates() {}

  @Test
  void testTryActivateNextOnEmptyQueue() {}

  @Test
  void testQueueCleanupAfterActivation() {}

  @Test
  void testMultipleContexts() {}
}
