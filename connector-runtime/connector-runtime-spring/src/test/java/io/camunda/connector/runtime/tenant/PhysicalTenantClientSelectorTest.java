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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.outbound.JobContext;
import org.junit.jupiter.api.Test;

class PhysicalTenantClientSelectorTest {

  private static CamundaClient clientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }

  private static JobContext jobOfPhysicalTenant(String physicalTenantId) {
    var jobContext = mock(JobContext.class);
    when(jobContext.getPhysicalTenantId()).thenReturn(physicalTenantId);
    return jobContext;
  }

  @Test
  void routesEachJobToItsOwnPhysicalTenantsClient() {
    var tenantA = clientWithPhysicalTenantId("tenanta");
    var tenantB = clientWithPhysicalTenantId("tenantb");
    var selector = new PhysicalTenantClientSelector(clientProvider(tenantA, tenantB));

    assertThat(selector.forJob(jobOfPhysicalTenant("tenanta"))).isSameAs(tenantA);
    assertThat(selector.forJob(jobOfPhysicalTenant("tenantb"))).isSameAs(tenantB);
  }

  @Test
  void failsRatherThanRoutingAJobOfAnUnconfiguredPhysicalTenantToAnotherTenantsClient() {
    var selector =
        new PhysicalTenantClientSelector(
            clientProvider(
                clientWithPhysicalTenantId("tenanta"), clientWithPhysicalTenantId("tenantb")));

    assertThatThrownBy(() -> selector.forJob(jobOfPhysicalTenant("tenantc")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenantc")
        .hasMessageContaining("tenanta")
        .hasMessageContaining("tenantb");
  }

  @Test
  void failsForAJobWithoutAPhysicalTenantWhenSeveralClientsAreConfigured() {
    var selector =
        new PhysicalTenantClientSelector(
            clientProvider(
                clientWithPhysicalTenantId("tenanta"), clientWithPhysicalTenantId("tenantb")));

    assertThatThrownBy(() -> selector.forJob(jobOfPhysicalTenant(null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void usesTheOnlyConfiguredClientForAJobCarryingNoPhysicalTenant() {
    var client = clientWithPhysicalTenantId(null);
    var selector = new PhysicalTenantClientSelector(clientProvider(client));

    assertThat(selector.forJob(jobOfPhysicalTenant(null))).isSameAs(client);
  }

  /**
   * A single-client runtime may well be configured with a physical tenant while its jobs still
   * carry none, so the sole client has to win regardless of what the job reports.
   */
  @Test
  void usesTheOnlyConfiguredClientWhateverPhysicalTenantIsRequested() {
    var client = clientWithPhysicalTenantId("tenanta");
    var selector = new PhysicalTenantClientSelector(clientProvider(client));

    assertThat(selector.forJob(jobOfPhysicalTenant(null))).isSameAs(client);
    assertThat(selector.forJob(jobOfPhysicalTenant("something-else"))).isSameAs(client);
  }

  @Test
  void resolvesOnFirstUseSoAClientNotYetInitializedAtStartupDoesNotFailTheContext() {
    var uninitialized = mock(CamundaClient.class);
    when(uninitialized.getConfiguration()).thenThrow(new RuntimeException("not initialized"));

    var selector = new PhysicalTenantClientSelector(clientProvider(uninitialized));

    assertThat(selector.forJob(jobOfPhysicalTenant(null))).isSameAs(uninitialized);
  }

  @Test
  void failsClearlyWhenTwoClientsClaimTheSamePhysicalTenant() {
    var selector =
        new PhysicalTenantClientSelector(
            clientProvider(
                clientWithPhysicalTenantId("tenanta"), clientWithPhysicalTenantId("tenanta")));

    assertThatThrownBy(() -> selector.forJob(jobOfPhysicalTenant("tenanta")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unique physical-tenant-id");
  }

  @Test
  void failsClearlyWhenSeveralClientsAreConfiguredWithoutAnyPhysicalTenantId() {
    var selector =
        new PhysicalTenantClientSelector(
            clientProvider(clientWithPhysicalTenantId(null), clientWithPhysicalTenantId(null)));

    assertThatThrownBy(() -> selector.forJob(jobOfPhysicalTenant("tenanta")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("physical-tenant-id");
  }

  @Test
  void failsClearlyWhenNoClientIsConfiguredAtAll() {
    var selector = new PhysicalTenantClientSelector(clientProvider());

    assertThatThrownBy(() -> selector.forJob(jobOfPhysicalTenant("tenanta")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No CamundaClient configured");
  }
}
