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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link InboundCorrelationConfiguration#inboundCorrelationHandler}, the
 * backward-compatible scalar bean built on top of the per-physical-tenant correlation-handler map.
 */
class InboundCorrelationConfigurationTest {

  private final InboundCorrelationConfiguration configuration =
      new InboundCorrelationConfiguration();

  private static CamundaClient clientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }

  @Test
  void inboundCorrelationHandler_returnsTheSingleEntryForASinglePhysicalTenant() {
    var registry = mock(CamundaClientRegistry.class);
    var client = clientWithPhysicalTenantId("tenant");
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));
    when(registry.get("engine-a")).thenReturn(client);

    var result =
        configuration.inboundCorrelationHandler(
            registry, null, mock(ObjectMapper.class), mock(ConnectorsInboundMetrics.class));

    assertThat(result).isInstanceOf(InboundCorrelationHandler.class);
  }

  @Test
  void inboundCorrelationHandler_throwsClearErrorForMultiplePhysicalTenants() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("tenant-a");
    var clientB = clientWithPhysicalTenantId("tenant-b");
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));
    when(registry.get("engine-a")).thenReturn(clientA);
    when(registry.get("engine-b")).thenReturn(clientB);

    assertThatThrownBy(
            () ->
                configuration.inboundCorrelationHandler(
                    registry, null, mock(ObjectMapper.class), mock(ConnectorsInboundMetrics.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("InboundCorrelationHandler");
  }
}
