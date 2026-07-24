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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;

class PhysicalTenantIdRoutingInboundConnectorContextFactoryTest {

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
}
