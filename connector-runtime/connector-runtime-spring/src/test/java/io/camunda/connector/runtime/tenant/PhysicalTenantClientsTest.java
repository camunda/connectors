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
package io.camunda.connector.runtime.tenant;

import static io.camunda.connector.runtime.TestCamundaClientProviders.clientProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.Test;

/** Covers the {@code CamundaClient} resolution behind the startup failure of #8977. */
class PhysicalTenantClientsTest {

  @Test
  void legacyClientResolvesTheSoleOrPrimaryClient() {
    var client = mock(CamundaClient.class);

    assertThat(PhysicalTenantClients.legacyClient(clientProvider(client))).isSameAs(client);
  }

  @Test
  void legacyClientResolvesToNothingWhenSeveralClientsHaveNoPrimary() {
    assertThat(
            PhysicalTenantClients.legacyClient(
                clientProvider(mock(CamundaClient.class), mock(CamundaClient.class))))
        .isNull();
  }

  @Test
  void defaultClientUsesTheSoleOrPrimaryClient() {
    var client = mock(CamundaClient.class);

    assertThat(PhysicalTenantClients.defaultClient(clientProvider(client), "someBean"))
        .isSameAs(client);
  }

  @Test
  void defaultClientFallsBackToTheFirstConfiguredClientWhenNoPrimaryIsDesignated() {
    var engineA = mock(CamundaClient.class);
    var engineB = mock(CamundaClient.class);

    var result = PhysicalTenantClients.defaultClient(clientProvider(engineA, engineB), "someBean");

    assertThat(result).isSameAs(engineA);
  }

  @Test
  void defaultClientFailsWithTheBeanNameWhenNoClientIsConfiguredAtAll() {
    assertThatThrownBy(() -> PhysicalTenantClients.defaultClient(clientProvider(), "someBean"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("someBean");
  }
}
