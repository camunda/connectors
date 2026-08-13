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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.common.AbstractConnectorFactory.ConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import io.camunda.connector.runtime.outbound.jobstream.BrokerConnectivityState;
import io.camunda.connector.runtime.outbound.jobstream.BrokerJobStreamClient;
import io.camunda.connector.runtime.outbound.jobstream.BrokerStreamsResult;
import io.camunda.connector.runtime.outbound.jobstream.RemoteJobStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class OutboundConnectorsServiceTest {

  private static final String RUNTIME_ID = "test-node";
  private static final String TYPE = "io.camunda:http-json:1";
  private static final String OTHER_TYPE = "io.camunda:rabbitmq:1";
  private static final String STREAM_ID = "stream-abc-123";
  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  private final OutboundConnectorFactory factory = mock(OutboundConnectorFactory.class);
  private final BrokerJobStreamClient brokerClient = mock(BrokerJobStreamClient.class);
  private final BrokerJobStreamClient otherBrokerClient = mock(BrokerJobStreamClient.class);

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
  // Broker client not configured / unreachable
  // ---------------------------------------------------------------------------

  @Test
  void shouldReturnUnknown_whenNoBrokerClientConfigured() {
    var service = new OutboundConnectorsService(factory);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.UNKNOWN);
    assertThat(results.getFirst().streamIds()).isNull();
  }

  @Test
  void shouldReturnUnknown_whenBrokerClientThrows() throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenThrow(new RuntimeException("connection refused"));

    var service = new OutboundConnectorsService(factory, brokerClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.UNKNOWN);
    assertThat(results.getFirst().streamIds()).isNull();
  }

  // ---------------------------------------------------------------------------
  // Broker client returns data
  // ---------------------------------------------------------------------------

  @Test
  void shouldReturnNone_whenBrokerReturnsNoConsumersForType() throws Exception {
    // A broker exists for TYPE but has no consumers
    when(brokerClient.fetchRemoteStreams())
        .thenReturn(
            new BrokerStreamsResult(List.of(List.of(new RemoteJobStream(TYPE, List.of())))));

    var service = new OutboundConnectorsService(factory, brokerClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.NONE);
    assertThat(results.getFirst().streamIds()).isNull();
  }

  @Test
  void shouldReturnAllConnected_whenAllBrokersHaveConsumer() throws Exception {
    var broker1 = new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)));
    var broker2 = new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)));
    when(brokerClient.fetchRemoteStreams())
        .thenReturn(new BrokerStreamsResult(List.of(List.of(broker1), List.of(broker2))));

    var service = new OutboundConnectorsService(factory, brokerClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results).hasSize(1);
    var response = results.getFirst();
    assertThat(response.brokerConnectivityState()).isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
    assertThat(response.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void shouldReturnPartiallyConnected_whenOnlyOneBrokerHasConsumer() throws Exception {
    var connectedBroker = new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)));
    var disconnectedBroker = new RemoteJobStream(TYPE, List.of());
    when(brokerClient.fetchRemoteStreams())
        .thenReturn(
            new BrokerStreamsResult(
                List.of(List.of(connectedBroker), List.of(disconnectedBroker))));

    var service = new OutboundConnectorsService(factory, brokerClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results.getFirst().brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  @Test
  void shouldReturnNone_whenBrokerClientReturnsEmptyList() throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var service = new OutboundConnectorsService(factory, brokerClient);
    var results = service.findAll(RUNTIME_ID);

    assertThat(results.getFirst().brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.NONE);
  }

  @Test
  void shouldReturnAllConnected_whenSingleBrokerSplitsJobTypeAcrossMultipleEntries()
      throws Exception {
    // Reproduces the reported bug: one broker reports TYPE as two separate remote-stream entries
    // (one consumer each) instead of one entry listing both consumers. With a single broker
    // queried, the connector must still be reported as fully connected, not degraded.
    var entry1 = new RemoteJobStream(TYPE, List.of(Map.of("id", "stream-1")));
    var entry2 = new RemoteJobStream(TYPE, List.of(Map.of("id", "stream-2")));
    when(brokerClient.fetchRemoteStreams())
        .thenReturn(new BrokerStreamsResult(List.of(List.of(entry1, entry2))));

    var service = new OutboundConnectorsService(factory, brokerClient);
    var response = service.findAll(RUNTIME_ID).getFirst();

    assertThat(response.brokerConnectivityState()).isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
    assertThat(response.streamIds()).containsExactlyInAnyOrder("stream-1", "stream-2");
  }

  // ---------------------------------------------------------------------------
  // Metadata correctness
  // ---------------------------------------------------------------------------

  @Test
  void shouldPopulateResponseMetadata() throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var service = new OutboundConnectorsService(factory, brokerClient);
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
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var service = new OutboundConnectorsService(factory, brokerClient);
    var results = service.findByType(TYPE, RUNTIME_ID);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().type()).isEqualTo(TYPE);
  }

  @Test
  void findByType_shouldThrowDataNotFoundException_whenTypeUnknown() throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var service = new OutboundConnectorsService(factory, brokerClient);

    assertThatThrownBy(() -> service.findByType("io.camunda:unknown:1", RUNTIME_ID))
        .isInstanceOf(DataNotFoundException.class);
  }

  // ---------------------------------------------------------------------------
  // Multiple physical tenants (engines)
  // ---------------------------------------------------------------------------

  @Test
  void shouldReportNoPhysicalTenant_whenWiredWithoutPhysicalTenantIds() throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var service = new OutboundConnectorsService(factory, brokerClient);

    assertThat(service.findAll(RUNTIME_ID)).singleElement().extracting("physicalTenantId").isNull();
  }

  @Test
  void shouldReportEachConnectorOncePerPhysicalTenant() throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));
    when(otherBrokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var results = multiTenantService().findAll(RUNTIME_ID);

    assertThat(results).hasSize(2);
    assertThat(results).allMatch(r -> TYPE.equals(r.type()));
    assertThat(results)
        .extracting(OutboundConnectorResponse::physicalTenantId)
        .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
  }

  @Test
  void shouldReportEachPhysicalTenantsOwnBrokerConnectivity() throws Exception {
    when(brokerClient.fetchRemoteStreams())
        .thenReturn(
            new BrokerStreamsResult(
                List.of(List.of(new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)))))));
    when(otherBrokerClient.fetchRemoteStreams())
        .thenReturn(
            new BrokerStreamsResult(List.of(List.of(new RemoteJobStream(TYPE, List.of())))));

    var results = multiTenantService().findAll(RUNTIME_ID);

    var tenantA = responseOf(results, TENANT_A);
    assertThat(tenantA.brokerConnectivityState()).isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
    assertThat(tenantA.streamIds()).containsExactly(STREAM_ID);

    var tenantB = responseOf(results, TENANT_B);
    assertThat(tenantB.brokerConnectivityState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(tenantB.streamIds()).isNull();
  }

  @Test
  void shouldReportUnknown_forPhysicalTenantWithoutBrokerClient() throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var service =
        new OutboundConnectorsService(
            factory, List.of(TENANT_A, TENANT_B), Map.of(TENANT_A, brokerClient));

    var results = service.findAll(RUNTIME_ID);

    assertThat(responseOf(results, TENANT_A).brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.NONE);
    assertThat(responseOf(results, TENANT_B).brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.UNKNOWN);
  }

  @Test
  void shouldStillReportOtherPhysicalTenants_whenOneEnginesBrokersAreUnreachable()
      throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenThrow(new RuntimeException("connection refused"));
    when(otherBrokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var results = multiTenantService().findAll(RUNTIME_ID);

    assertThat(responseOf(results, TENANT_A).brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.UNKNOWN);
    assertThat(responseOf(results, TENANT_B).brokerConnectivityState())
        .isEqualTo(BrokerConnectivityState.NONE);
  }

  // ---------------------------------------------------------------------------
  // physicalTenantIds filter
  // ---------------------------------------------------------------------------

  @Test
  void shouldFilterByPhysicalTenantId() throws Exception {
    when(otherBrokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var results = multiTenantService().findAll(RUNTIME_ID, List.of(TENANT_B));

    assertThat(results)
        .singleElement()
        .extracting(OutboundConnectorResponse::physicalTenantId)
        .isEqualTo(TENANT_B);
    verify(brokerClient, never()).fetchRemoteStreams();
  }

  /** A {@code null} filter is what the endpoints pass when the query param is omitted entirely. */
  @ParameterizedTest
  @NullAndEmptySource
  void shouldReturnEveryPhysicalTenant_whenFilterIsNullOrEmpty(List<String> filter)
      throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));
    when(otherBrokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    assertThat(multiTenantService().findAll(RUNTIME_ID, filter))
        .extracting(OutboundConnectorResponse::physicalTenantId)
        .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void findByType_shouldReturnEveryPhysicalTenant_whenFilterIsNullOrEmpty(List<String> filter)
      throws Exception {
    when(brokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));
    when(otherBrokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    assertThat(multiTenantService().findByType(TYPE, RUNTIME_ID, filter))
        .extracting(OutboundConnectorResponse::physicalTenantId)
        .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
  }

  @Test
  void findByType_shouldFilterByPhysicalTenantId() throws Exception {
    when(otherBrokerClient.fetchRemoteStreams()).thenReturn(new BrokerStreamsResult(List.of()));

    var results = multiTenantService().findByType(TYPE, RUNTIME_ID, List.of(TENANT_B));

    assertThat(results)
        .singleElement()
        .extracting(OutboundConnectorResponse::physicalTenantId)
        .isEqualTo(TENANT_B);
  }

  @Test
  void findByType_shouldThrowDataNotFoundException_whenFilterMatchesNoPhysicalTenant() {
    var service = multiTenantService();

    assertThatThrownBy(() -> service.findByType(TYPE, RUNTIME_ID, List.of("unknown-tenant")))
        .isInstanceOf(DataNotFoundException.class);
  }

  private OutboundConnectorsService multiTenantService() {
    return new OutboundConnectorsService(
        factory,
        List.of(TENANT_A, TENANT_B),
        Map.of(TENANT_A, brokerClient, TENANT_B, otherBrokerClient));
  }

  private static OutboundConnectorResponse responseOf(
      List<OutboundConnectorResponse> responses, String physicalTenantId) {
    return responses.stream()
        .filter(r -> physicalTenantId.equals(r.physicalTenantId()))
        .findFirst()
        .orElseThrow();
  }
}
