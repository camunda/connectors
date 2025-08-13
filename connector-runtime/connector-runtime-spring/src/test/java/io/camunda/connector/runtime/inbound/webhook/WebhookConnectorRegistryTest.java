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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WebhookConnectorRegistryTest extends WebhookTestsBase {

  @Test
  public void multipleWebhooksOnSameContextPathAreQueued() {
    WebhookConnectorRegistry webhookConnectorRegistry = new WebhookConnectorRegistry();
    var connectorA = buildConnector("processA", 1, "myPath");
    webhookConnectorRegistry.register(connectorA);

    var connectorB = buildConnector("processB", 1, "myPath");
    // connectorA is registered, but connectorB is queued
    webhookConnectorRegistry.register(connectorB);

    // connectorB should be the active connector now
    webhookConnectorRegistry.deregister(connectorA);

    assertFalse(isRegistered(webhookConnectorRegistry, connectorA));
    assertTrue(isRegistered(webhookConnectorRegistry, connectorB));
    assertThat(connectorB.context().getHealth()).isEqualTo(Health.up());
  }

  @Test
  public void multipleWebhooksOnSameContextPathAreSupported() {
    WebhookConnectorRegistry webhookConnectorRegistry = new WebhookConnectorRegistry();
    var connectorA = buildConnector("processA", 1, "myPath");
    webhookConnectorRegistry.register(connectorA);

    var connectorB = buildConnector("processA", 1, "myPath");
    webhookConnectorRegistry.register(connectorB);
    assertTrue(isRegistered(webhookConnectorRegistry, connectorB));
    assertThat(connectorB.context().getHealth())
        .isEqualTo(
            Health.down(
                new IllegalStateException(
                    "Context: myPath already in use by: process processA(testElement)")));
  }

  @Test
  public void webhookMultipleVersionsDisableWebhook() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    var processA1 = buildConnector("processA", 1, "myPath");
    var processA2 = buildConnector("processA", 2, "myPath");
    var processB1 = buildConnector("processB", 1, "myPath2");

    webhook.register(processA1);

    // create a new object to ensure correct instance comparison
    var processA1Copy = buildConnector("processA", 1, "myPath");
    webhook.deregister(processA1Copy);

    webhook.register(processA2);

    webhook.register(processB1);
    var processB1Copy = buildConnector("processB", 1, "myPath2");
    webhook.deregister(processB1Copy);

    var connectorForPath1 = webhook.getActiveWebhook("myPath");

    assertTrue(connectorForPath1.isPresent(), "A2 context is present");
    assertTrue(isRegistered(webhook, processA2), "A2 is registered");
    assertFalse(isRegistered(webhook, processA1), "A1 is not registered");
    assertFalse(isRegistered(webhook, processB1), "B1 is not registered");
    assertEquals(
        2,
        connectorForPath1.get().context().getDefinition().elements().getFirst().version(),
        "The newest one");

    var connectorForPath2 = webhook.getActiveWebhook("myPath2");
    assertTrue(connectorForPath2.isEmpty(), "No one - as it was deleted.");
  }

  @Test
  public void webhookDeactivation_shouldReturnNotFound() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector("processA", 1, "myPath");

    // when
    webhook.register(processA1);
    webhook.deregister(processA1);

    // then
    assertFalse(webhook.getActiveWebhook("myPath").isPresent());
    assertFalse(isRegistered(webhook, processA1));
  }

  @Test
  public void webhookDeactivation_samePathButDifferentConnector_shouldFail() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector("processA", 1, "myPath");
    var processA2 = buildConnector("processA", 2, "myPath");

    // when
    webhook.register(processA1);

    // then
    assertThrowsExactly(RuntimeException.class, () -> webhook.deregister(processA2));
    assertFalse(isRegistered(webhook, processA2));
    assertTrue(isRegistered(webhook, processA1));
  }
}
