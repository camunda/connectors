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
package io.camunda.connector.runtime.instances.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.common.AbstractConnectorFactory.ConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.outbound.jobstream.GatewayConnectivityState;
import io.camunda.connector.runtime.outbound.jobstream.GatewayJobStreamClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboundConnectorsServiceTest {

  private static final String RUNTIME_ID = "test-node";
  private static final String TYPE = "io.camunda:http-json:1";

  private final OutboundConnectorFactory factory = mock(OutboundConnectorFactory.class);

  @Test
  void shouldReturnUnknown_whenNoGatewayClientConfigured() {
    when(factory.getRuntimeConfigurations())
        .thenReturn(
            List.of(
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "HTTP JSON", new String[] {"url"}, TYPE, () -> null, null),
                    true)));

    var service = new OutboundConnectorsService(factory, Optional.empty());
    var results = service.findAll(RUNTIME_ID);

    assertEquals(1, results.size());
    assertEquals(GatewayConnectivityState.UNKNOWN, results.getFirst().gatewayConnectivityState());
  }

  @Test
  void shouldReturnUnreachable_whenGatewayClientThrows() throws Exception {
    when(factory.getRuntimeConfigurations())
        .thenReturn(
            List.of(
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "HTTP JSON", new String[] {"url"}, TYPE, () -> null, null),
                    true)));

    GatewayJobStreamClient client = mock(GatewayJobStreamClient.class);
    when(client.fetchJobStreams()).thenThrow(new RuntimeException("connection refused"));

    var service = new OutboundConnectorsService(factory, Optional.of(client));
    var results = service.findAll(RUNTIME_ID);

    assertEquals(1, results.size());
    assertEquals(
        GatewayConnectivityState.UNREACHABLE, results.getFirst().gatewayConnectivityState());
  }
}
