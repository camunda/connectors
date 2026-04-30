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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.common.AbstractConnectorFactory.ConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.camunda.connector.runtime.outbound.jobstream.BrokerConnectivityState;
import io.camunda.connector.runtime.outbound.jobstream.ClientJobStream;
import io.camunda.connector.runtime.outbound.jobstream.ClientStreamId;
import io.camunda.connector.runtime.outbound.jobstream.GatewayConnectivityState;
import io.camunda.connector.runtime.outbound.jobstream.GatewayJobStreamClient;
import io.camunda.connector.runtime.outbound.jobstream.JobStreamsResponse;
import io.camunda.connector.runtime.outbound.jobstream.RemoteJobStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboundConnectorsServiceTest {

  private static final String RUNTIME_ID = "test-node";
  private static final String TYPE = "io.camunda:http-json:1";
  private static final String OTHER_TYPE = "io.camunda:rabbitmq:1";
  private static final String STREAM_ID = "stream-abc-123";

  private final OutboundConnectorFactory factory = mock(OutboundConnectorFactory.class);
  private final GatewayJobStreamClient gatewayClient = mock(GatewayJobStreamClient.class);

  @BeforeEach
  void setupFactory() {
    when(factory.getRuntimeConfigurations())
        .thenReturn(
            List.of(
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "HTTP JSON", new String[] {"url"}, TYPE, () -> null, null),
                    true)));
  }

  // ---------------------------------------------------------------------------
  // Gateway not configured / unreachable
  // ---------------------------------------------------------------------------

  @Test
  void shouldReturnUnknown_whenNoGatewayClientConfigured() {
    var service = new OutboundConnectorsService(factory);
    var results = service.findAll(RUNTIME_ID);

    assertEquals(1, results.size());
    assertEquals(GatewayConnectivityState.UNKNOWN, results.getFirst().gatewayConnectivityState());
    assertThat(results.getFirst().brokerConnectivityState()).isNull();
    assertThat(results.getFirst().streamIds()).isNull();
  }

  @Test
  void shouldReturnUnreachable_whenGatewayClientThrows() throws Exception {
    when(gatewayClient.fetchJobStreams()).thenThrow(new RuntimeException("connection refused"));

    var service = new OutboundConnectorsService(factory, gatewayClient);
    var results = service.findAll(RUNTIME_ID);

    assertEquals(1, results.size());
    assertEquals(
        GatewayConnectivityState.UNREACHABLE, results.getFirst().gatewayConnectivityState());
    assertThat(results.getFirst().brokerConnectivityState()).isNull();
    assertThat(results.getFirst().streamIds()).isNull();
  }

  // ---------------------------------------------------------------------------
  // Gateway reachable — client stream absent
  // ---------------------------------------------------------------------------

  @Test
  void shouldReturnNotConnected_whenNoClientStreamForJobType() throws Exception {
    when(gatewayClient.fetchJobStreams())
        .thenReturn(new JobStreamsResponse(List.of(), List.of())); // no streams at all

    var service = new OutboundConnectorsService(factory, gatewayClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().gatewayConnectivityState())
        .isEqualTo(GatewayConnectivityState.NOT_CONNECTED);
    assertThat(results.getFirst().brokerConnectivityState()).isNull();
    assertThat(results.getFirst().streamIds()).isNull();
  }

  // ---------------------------------------------------------------------------
  // Gateway reachable — client stream present
  // ---------------------------------------------------------------------------

  @Test
  void shouldReturnConnectedAndAllBrokers_whenFullyConnected() throws Exception {
    var clientStream = new ClientJobStream(TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var brokerStream = new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)));
    when(gatewayClient.fetchJobStreams())
        .thenReturn(new JobStreamsResponse(List.of(brokerStream), List.of(clientStream)));

    var service = new OutboundConnectorsService(factory, gatewayClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results).hasSize(1);
    var response = results.getFirst();
    assertThat(response.gatewayConnectivityState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(response.brokerConnectivityState()).isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
    assertThat(response.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void shouldReturnPartiallyConnected_whenOnlyOneBrokerHasConsumer() throws Exception {
    var clientStream = new ClientJobStream(TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var connectedBroker = new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)));
    var disconnectedBroker = new RemoteJobStream(TYPE, List.of());
    when(gatewayClient.fetchJobStreams())
        .thenReturn(
            new JobStreamsResponse(
                List.of(connectedBroker, disconnectedBroker), List.of(clientStream)));

    var service = new OutboundConnectorsService(factory, gatewayClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results.getFirst().brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  @Test
  void shouldReturnNoneBrokerState_whenNoRemoteStreamsForJobType() throws Exception {
    var clientStream = new ClientJobStream(TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    when(gatewayClient.fetchJobStreams())
        .thenReturn(new JobStreamsResponse(List.of(), List.of(clientStream)));

    var service = new OutboundConnectorsService(factory, gatewayClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results.getFirst().brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.NONE);
  }

  // ---------------------------------------------------------------------------
  // Metadata correctness
  // ---------------------------------------------------------------------------

  @Test
  void shouldPopulateResponseMetadata() throws Exception {
    when(gatewayClient.fetchJobStreams()).thenReturn(new JobStreamsResponse(List.of(), List.of()));

    var service = new OutboundConnectorsService(factory, gatewayClient);
    var response = service.findAll(RUNTIME_ID).getFirst();

    assertThat(response.name()).isEqualTo("HTTP JSON");
    assertThat(response.type()).isEqualTo(TYPE);
    assertThat(response.inputVariables()).containsExactly("url");
    assertThat(response.runtimeId()).isEqualTo(RUNTIME_ID);
    assertThat(response.enabled()).isTrue();
  }

  // ---------------------------------------------------------------------------
  // findByType
  // ---------------------------------------------------------------------------

  @Test
  void findByType_shouldReturnOnlyMatchingType() throws Exception {
    when(factory.getRuntimeConfigurations())
        .thenReturn(
            List.of(
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "HTTP JSON", new String[] {"url"}, TYPE, () -> null, null),
                    true),
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "RabbitMQ", new String[] {"message"}, OTHER_TYPE, () -> null, null),
                    true)));
    when(gatewayClient.fetchJobStreams()).thenReturn(new JobStreamsResponse(List.of(), List.of()));

    var service = new OutboundConnectorsService(factory, gatewayClient);
    var results = service.findByType(TYPE, RUNTIME_ID);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().type()).isEqualTo(TYPE);
  }

  @Test
  void findByType_shouldThrowDataNotFoundException_whenTypeUnknown() throws Exception {
    when(gatewayClient.fetchJobStreams()).thenReturn(new JobStreamsResponse(List.of(), List.of()));

    var service = new OutboundConnectorsService(factory, gatewayClient);

    assertThatThrownBy(() -> service.findByType("io.camunda:unknown:1", RUNTIME_ID))
        .isInstanceOf(DataNotFoundException.class);
  }
}
