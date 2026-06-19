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
package io.camunda.connector.runtime.outbound.jobstream;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

@WireMockTest
class BrokerJobStreamClientTest {

  private static final String JOB_STREAMS_PATH = "/actuator/jobstreams";
  private static final String JOB_TYPE = "io.camunda:http-json:1";
  private static final String STREAM_ID = "stream-abc-123";

  // ---------------------------------------------------------------------------
  // Explicit-addresses mode
  // ---------------------------------------------------------------------------

  @Test
  void fetchRemoteStreams_shouldReturnRemoteStreams_withExplicitAddress(
      WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(
        get(JOB_STREAMS_PATH)
            .willReturn(
                okJson(
                    """
                    {
                      "remote": [
                        {"jobType": "io.camunda:http-json:1", "consumers": [{"id": "stream-abc-123"}]}
                      ],
                      "client": []
                    }
                    """)));
    var client = clientWithAddresses(wmRuntimeInfo.getHttpPort(), "localhost");

    var result = client.fetchRemoteStreams();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().jobType()).isEqualTo(JOB_TYPE);
    assertThat(result.getFirst().consumers().getFirst()).containsEntry("id", STREAM_ID);
    verify(getRequestedFor(urlEqualTo(JOB_STREAMS_PATH)));
  }

  @Test
  void fetchRemoteStreams_shouldAggregateStreams_fromMultipleExplicitAddresses(
      WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(
        get(JOB_STREAMS_PATH)
            .willReturn(
                okJson(
                    """
                    {
                      "remote": [
                        {"jobType": "io.camunda:http-json:1", "consumers": [{"id": "stream-abc-123"}]}
                      ],
                      "client": []
                    }
                    """)));
    int port = wmRuntimeInfo.getHttpPort();
    // Two explicit addresses pointing at the same mock (simulates 2 brokers)
    var client = clientWithAddresses(port, "localhost", "localhost");

    var result = client.fetchRemoteStreams();

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(s -> JOB_TYPE.equals(s.jobType()));
    verify(2, getRequestedFor(urlEqualTo(JOB_STREAMS_PATH)));
  }

  @Test
  void fetchRemoteStreams_shouldReturnEmptyList_whenBrokerHasNoRemoteStreams(
      WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(
        get(JOB_STREAMS_PATH)
            .willReturn(
                okJson(
                    """
            {"remote": [], "client": []}
            """)));
    var client = clientWithAddresses(wmRuntimeInfo.getHttpPort(), "localhost");

    assertThat(client.fetchRemoteStreams()).isEmpty();
  }

  @Test
  void fetchRemoteStreams_shouldThrowIOException_whenBrokerReturnsNon200(
      WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(get(JOB_STREAMS_PATH).willReturn(serverError().withBody("boom")));
    var client = clientWithAddresses(wmRuntimeInfo.getHttpPort(), "localhost");

    assertThatThrownBy(client::fetchRemoteStreams)
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Broker job-streams endpoint returned status 500")
        .hasMessageContaining(JOB_STREAMS_PATH);
  }

  // ---------------------------------------------------------------------------
  // Topology-discovery mode (fallback)
  // ---------------------------------------------------------------------------

  @Test
  void fetchRemoteStreams_shouldDiscoverBrokersFromTopology_whenNoAddressesConfigured(
      WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(
        get(JOB_STREAMS_PATH)
            .willReturn(
                okJson(
                    """
                    {
                      "remote": [
                        {"jobType": "io.camunda:http-json:1", "consumers": [{"id": "stream-abc-123"}]}
                      ],
                      "client": []
                    }
                    """)));
    var client = clientFromTopology(wmRuntimeInfo.getHttpPort(), "localhost");

    var result = client.fetchRemoteStreams();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().jobType()).isEqualTo(JOB_TYPE);
    verify(getRequestedFor(urlEqualTo(JOB_STREAMS_PATH)));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private BrokerJobStreamClient clientWithAddresses(int port, String... hosts) {
    List<URI> uris = Arrays.stream(hosts).map(h -> URI.create("http://" + h + ":" + port)).toList();
    return new BrokerJobStreamClient(uris, ConnectorsObjectMapperSupplier.getCopy());
  }

  private BrokerJobStreamClient clientFromTopology(int port, String... hosts) {
    CamundaClient camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    Topology topology = mock(Topology.class);
    List<BrokerInfo> brokers =
        Arrays.stream(hosts)
            .map(
                host -> {
                  BrokerInfo broker = mock(BrokerInfo.class);
                  when(broker.getHost()).thenReturn(host);
                  return broker;
                })
            .toList();
    when(topology.getBrokers()).thenReturn(brokers);
    when(camundaClient.newTopologyRequest().send().join()).thenReturn(topology);

    return new BrokerJobStreamClient(camundaClient, port, ConnectorsObjectMapperSupplier.getCopy());
  }
}
