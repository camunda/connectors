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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PhysicalTenantIdRoutingInboundConnectorContextFactoryTest {

  private final InboundConnectorRuntimeConfiguration configuration =
      new InboundConnectorRuntimeConfiguration();

  private static CamundaClient clientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }

  private static ValidInboundConnectorDetails detailsFor(String physicalTenantId) {
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData(
                "id",
                null,
                null,
                0,
                0,
                "elementId",
                null,
                null,
                "tenant",
                physicalTenantId,
                new ElementTemplateDetails("Test", "1", "icon"),
                Map.of()));
    var details = mock(ValidInboundConnectorDetails.class);
    when(details.connectorElements()).thenReturn(List.of(element));
    return details;
  }

  @Test
  void routesToTheDelegateForTheConnectorsPhysicalTenant() {
    var delegateA = mock(InboundConnectorContextFactory.class);
    var delegateB = mock(InboundConnectorContextFactory.class);
    var contextA = mock(InboundConnectorContext.class);
    when(delegateA.createContext(any(), any(), any(), any())).thenReturn(contextA);

    var routingFactory =
        new PhysicalTenantIdRoutingInboundConnectorContextFactory(
            Map.of("physical-tenant-a", delegateA, "physical-tenant-b", delegateB));

    var details = detailsFor("physical-tenant-a");
    java.util.function.Consumer<Throwable> cancellationCallback = t -> {};
    io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogWriter logWriter = a -> {};
    var result =
        routingFactory.createContext(
            details, cancellationCallback, InboundConnectorExecutable.class, logWriter);

    assertThat(result).isSameAs(contextA);
    verify(delegateA)
        .createContext(details, cancellationCallback, InboundConnectorExecutable.class, logWriter);
    verify(delegateB, never()).createContext(any(), any(), any(), any());
  }

  @Test
  void routesDifferentPhysicalTenantsToDifferentDelegates() {
    var delegateA = mock(InboundConnectorContextFactory.class);
    var delegateB = mock(InboundConnectorContextFactory.class);
    var contextA = mock(InboundConnectorContext.class);
    var contextB = mock(InboundConnectorContext.class);
    when(delegateA.createContext(any(), any(), any(), any())).thenReturn(contextA);
    when(delegateB.createContext(any(), any(), any(), any())).thenReturn(contextB);

    var routingFactory =
        new PhysicalTenantIdRoutingInboundConnectorContextFactory(
            Map.of("physical-tenant-a", delegateA, "physical-tenant-b", delegateB));

    var resultA =
        routingFactory.createContext(
            detailsFor("physical-tenant-a"), t -> {}, InboundConnectorExecutable.class, (a) -> {});
    var resultB =
        routingFactory.createContext(
            detailsFor("physical-tenant-b"), t -> {}, InboundConnectorExecutable.class, (a) -> {});

    assertThat(resultA).isSameAs(contextA);
    assertThat(resultB).isSameAs(contextB);
  }

  @Test
  void throwsClearErrorForUnknownPhysicalTenant() {
    var delegateA = mock(InboundConnectorContextFactory.class);
    var routingFactory =
        new PhysicalTenantIdRoutingInboundConnectorContextFactory(
            Map.of("physical-tenant-a", delegateA));

    var details = detailsFor("physical-tenant-unknown");

    assertThatThrownBy(
            () ->
                routingFactory.createContext(
                    details, t -> {}, InboundConnectorExecutable.class, (a) -> {}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("physical-tenant-unknown");
  }

  @Test
  void resolvesDelegateByClientNameAfterFallbackMigrationWhenRegistrySupplied() {
    // delegatesByPhysicalTenantId is built once at startup, keyed "engine-c" (the client's own
    // name) because its real physical tenant ID wasn't readable yet. A later onStart migrates the
    // SAME client's registry entry to "resolved-tenant", so freshly-imported connector elements are
    // tagged "resolved-tenant" from then on -- this factory must still find the "engine-c" delegate
    // for them, since the delegate itself was never rebuilt.
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("engine-c"));
    var uninitializedClient = mock(CamundaClient.class);
    when(uninitializedClient.getConfiguration())
        .thenThrow(new RuntimeException("client not initialized"))
        .thenReturn(clientWithPhysicalTenantId("resolved-tenant").getConfiguration());
    when(registry.get("engine-c")).thenReturn(uninitializedClient);
    var searchQueryClientRegistry =
        configuration.searchQueryClientRegistry(registry, null, null, 200);
    searchQueryClientRegistry.onStart(uninitializedClient, "engine-c");
    assertThat(searchQueryClientRegistry.snapshot()).containsOnlyKeys("resolved-tenant");

    var delegate = mock(InboundConnectorContextFactory.class);
    var context = mock(InboundConnectorContext.class);
    when(delegate.createContext(any(), any(), any(), any())).thenReturn(context);
    var routingFactory =
        new PhysicalTenantIdRoutingInboundConnectorContextFactory(
            Map.of("engine-c", delegate), searchQueryClientRegistry);

    var result =
        routingFactory.createContext(
            detailsFor("resolved-tenant"), t -> {}, InboundConnectorExecutable.class, (a) -> {});

    assertThat(result).isSameAs(context);
  }
}
