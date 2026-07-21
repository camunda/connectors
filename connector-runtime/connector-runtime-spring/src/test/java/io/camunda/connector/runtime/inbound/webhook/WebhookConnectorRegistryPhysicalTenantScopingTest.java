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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.api.inbound.Health;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Covers {@code appendPhysicalTenantAndTenantToPath} behavior: same raw webhook path across
 * different physical tenants and/or tenants should no longer collide once the flag is enabled,
 * while a true duplicate (same physical tenant + tenant + path) still queues via {@link
 * WebhookExecutables} exactly as today.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WebhookConnectorRegistryPhysicalTenantScopingTest extends WebhookTestsBase {

  @Test
  public void samePath_differentPhysicalTenant_bothActiveWhenFlagEnabled() {
    var registry = new WebhookConnectorRegistry(true);

    var connectorPhysicalTenantA =
        buildConnector("processA", 1, "myPath", "tenant", "physical-tenant-a");
    var connectorPhysicalTenantB =
        buildConnector("processB", 1, "myPath", "tenant", "physical-tenant-b");

    registry.register(connectorPhysicalTenantA);
    registry.register(connectorPhysicalTenantB);

    assertTrue(isRegistered(registry, connectorPhysicalTenantA));
    assertTrue(isRegistered(registry, connectorPhysicalTenantB));
    assertThat(connectorPhysicalTenantA.context().getHealth()).isEqualTo(Health.up());
    assertThat(connectorPhysicalTenantB.context().getHealth()).isEqualTo(Health.up());
    assertThat(registry.getActiveWebhook("physical-tenant-a", "tenant", "myPath"))
        .contains(connectorPhysicalTenantA);
    assertThat(registry.getActiveWebhook("physical-tenant-b", "tenant", "myPath"))
        .contains(connectorPhysicalTenantB);
  }

  @Test
  public void samePath_samePhysicalTenant_differentTenant_bothActiveWhenFlagEnabled() {
    var registry = new WebhookConnectorRegistry(true);

    var connectorTenantA = buildConnector("processA", 1, "myPath", "tenant-a", "physical-tenant");
    var connectorTenantB = buildConnector("processB", 1, "myPath", "tenant-b", "physical-tenant");

    registry.register(connectorTenantA);
    registry.register(connectorTenantB);

    assertTrue(isRegistered(registry, connectorTenantA));
    assertTrue(isRegistered(registry, connectorTenantB));
    assertThat(registry.getActiveWebhook("physical-tenant", "tenant-a", "myPath"))
        .contains(connectorTenantA);
    assertThat(registry.getActiveWebhook("physical-tenant", "tenant-b", "myPath"))
        .contains(connectorTenantB);
  }

  @Test
  public void samePath_samePhysicalTenant_sameTenant_stillQueuedWhenFlagEnabled() {
    var registry = new WebhookConnectorRegistry(true);

    var connectorA = buildConnector("processA", 1, "myPath", "tenant", "physical-tenant");
    var connectorB = buildConnector("processB", 1, "myPath", "tenant", "physical-tenant");

    registry.register(connectorA);
    registry.register(connectorB);

    assertTrue(isRegistered(registry, connectorA));
    assertTrue(isRegistered(registry, connectorB));
    assertThat(registry.getActiveWebhook("physical-tenant", "tenant", "myPath"))
        .contains(connectorA);
    assertThat(connectorB.context().getHealth().getStatus()).isEqualTo(Health.Status.DOWN);
  }

  @Test
  public void legacyTwoSegmentLookup_returnsEmpty_onceRegisteredOnlyUnderCompositeKey() {
    var registry = new WebhookConnectorRegistry(true);

    var connector = buildConnector("processA", 1, "myPath", "tenant", "physical-tenant");
    registry.register(connector);

    assertFalse(registry.getActiveWebhook("myPath").isPresent());
    assertTrue(registry.getActiveWebhook("physical-tenant", "tenant", "myPath").isPresent());
  }

  @Test
  public void flagDisabled_defaultConstructor_preservesLegacyPathOnlyKeying() {
    var registry = new WebhookConnectorRegistry();

    var connectorPhysicalTenantA =
        buildConnector("processA", 1, "myPath", "tenant", "physical-tenant-a");
    var connectorPhysicalTenantB =
        buildConnector("processB", 1, "myPath", "tenant", "physical-tenant-b");

    registry.register(connectorPhysicalTenantA);
    // flag off: same raw path collides regardless of physical tenant, exactly as before this
    // feature
    registry.register(connectorPhysicalTenantB);

    assertTrue(isRegistered(registry, connectorPhysicalTenantA));
    assertTrue(isRegistered(registry, connectorPhysicalTenantB));
    assertThat(connectorPhysicalTenantB.context().getHealth().getStatus())
        .isEqualTo(Health.Status.DOWN);
    assertThat(registry.getActiveWebhook("myPath")).contains(connectorPhysicalTenantA);
  }
}
