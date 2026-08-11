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
package io.camunda.connector.runtime.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.common.AbstractConnectorFactory.ConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.instances.service.OutboundConnectorsService;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import io.camunda.connector.runtime.outbound.jobstream.BrokerConnectivityState;
import io.camunda.connector.runtime.outbound.jobstream.BrokerJobStreamClient;
import io.camunda.connector.runtime.outbound.jobstream.BrokerStreamsResult;
import io.camunda.connector.runtime.outbound.jobstream.RemoteJobStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies how {@link OutboundConnectorRuntimeConfiguration} assembles the {@code
 * OutboundConnectorsService} across physical tenants (#7965): which engines end up in the listing,
 * and which {@link BrokerJobStreamClient} each of them is monitored through.
 *
 * <p>The {@code @Bean} method is invoked directly on a plain (non-CGLIB-proxied) configuration
 * instance, so the assembled service — not just the bean graph — is what is exercised.
 */
class OutboundConnectorsServiceWiringTest {

  private static final String TYPE = "io.camunda:http-json:1";
  private static final String STREAM_ID = "stream-abc-123";

  private final OutboundConnectorRuntimeConfiguration configuration =
      new OutboundConnectorRuntimeConfiguration();

  private final OutboundConnectorFactory connectorFactory = mock(OutboundConnectorFactory.class);
  private final BrokerJobStreamClient injectedBrokerClient = mock(BrokerJobStreamClient.class);

  @BeforeEach
  void registerOneConnector() {
    when(connectorFactory.getRuntimeConfigurations())
        .thenReturn(
            List.of(
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "HTTP JSON", new String[] {"url"}, TYPE, () -> null, null),
                    true)));
  }

  /**
   * A registry whose clients resolve to the given physical tenant IDs. Deep stubs make the topology
   * request each per-tenant {@link BrokerJobStreamClient} would issue resolve to zero brokers,
   * rather than attempting a real connection.
   */
  private static CamundaClientRegistry registryWith(String... physicalTenantIds) {
    var registry = mock(CamundaClientRegistry.class);
    var names = new LinkedHashSet<String>();
    for (String physicalTenantId : physicalTenantIds) {
      var clientName = "engine-" + physicalTenantId;
      names.add(clientName);
      var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
      when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
      when(registry.get(clientName)).thenReturn(client);
    }
    when(registry.clientNames()).thenReturn(names);
    return registry;
  }

  private OutboundConnectorsService serviceFor(
      CamundaClientRegistry registry, BrokerJobStreamClient injected, String addresses) {
    return configuration.outboundConnectorsService(
        connectorFactory,
        registry,
        null,
        injected,
        ConnectorsObjectMapperSupplier.getCopy(),
        9600,
        addresses);
  }

  @Test
  void listsEveryConfiguredPhysicalTenant_evenWithBrokerMonitoringDisabled() {
    var service = serviceFor(registryWith("tenanta", "tenantb"), null, null);

    var results = service.findAll("runtime-1");

    assertThat(results)
        .extracting(OutboundConnectorResponse::physicalTenantId)
        .containsExactlyInAnyOrder("tenanta", "tenantb");
    assertThat(results)
        .allMatch(r -> r.brokerConnectivityState() == BrokerConnectivityState.UNKNOWN);
  }

  @Test
  void reusesTheInjectedBrokerClient_forASinglePhysicalTenant() throws Exception {
    when(injectedBrokerClient.fetchRemoteStreams())
        .thenReturn(
            new BrokerStreamsResult(
                List.of(List.of(new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)))))));

    var service = serviceFor(registryWith("tenanta"), injectedBrokerClient, null);

    assertThat(service.findAll("runtime-1"))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.physicalTenantId()).isEqualTo("tenanta");
              assertThat(r.brokerConnectivityState())
                  .isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
            });
  }

  @Test
  void sharesTheExplicitAddressClient_acrossEveryPhysicalTenant() throws Exception {
    when(injectedBrokerClient.fetchRemoteStreams())
        .thenReturn(
            new BrokerStreamsResult(
                List.of(List.of(new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)))))));

    var service =
        serviceFor(
            registryWith("tenanta", "tenantb"), injectedBrokerClient, "http://localhost:9600");

    // the configured addresses are global, so both engines are monitored through the same client
    assertThat(service.findAll("runtime-1"))
        .hasSize(2)
        .allMatch(r -> r.brokerConnectivityState() == BrokerConnectivityState.ALL_CONNECTED);
  }

  @Test
  void buildsAPerPhysicalTenantClient_inTopologyDiscoveryMode() throws Exception {
    // would be reported as ALL_CONNECTED if the single injected client were (wrongly) shared
    when(injectedBrokerClient.fetchRemoteStreams())
        .thenReturn(
            new BrokerStreamsResult(
                List.of(List.of(new RemoteJobStream(TYPE, List.of(Map.of("id", STREAM_ID)))))));

    var service = serviceFor(registryWith("tenanta", "tenantb"), injectedBrokerClient, null);

    // each engine is monitored through its own client, whose topology reports no brokers at all
    assertThat(service.findAll("runtime-1"))
        .hasSize(2)
        .allMatch(r -> r.brokerConnectivityState() == BrokerConnectivityState.NONE);
  }
}
