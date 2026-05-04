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
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;

@WireMockTest
class GatewayJobStreamClientTest {

  private static final String JOB_STREAMS_PATH = "/actuator/jobstreams";
  private static final String REST_ADDRESS = "http://localhost:26500";
  private static final String JOB_TYPE = "io.camunda:http-json:1";
  private static final String STREAM_ID = "stream-abc-123";

  @Test
  void fetchJobStreams_shouldDeriveMonitoringEndpointFromRestAddressAndDeserializeResponse(
      WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(
        get(JOB_STREAMS_PATH)
            .willReturn(
                okJson(
                    """
                    {
                      "remote": [
                        {
                          "jobType": "io.camunda:http-json:1",
                          "consumers": [{"id": "stream-abc-123"}],
                          "ignoredRemoteField": "ignored"
                        }
                      ],
                      "client": [
                        {
                          "jobType": "io.camunda:http-json:1",
                          "id": {"serverStreamId": "stream-abc-123", "localId": 7},
                          "connectedTo": [0, 1],
                          "ignoredClientField": "ignored"
                        }
                      ],
                      "ignoredTopLevelField": "ignored"
                    }
                    """)));
    var client = newClient(wmRuntimeInfo.getHttpPort());

    JobStreamsResponse result = client.fetchJobStreams();

    assertThat(result.remote()).hasSize(1);
    assertThat(result.remote().getFirst().jobType()).isEqualTo(JOB_TYPE);
    assertThat(result.remote().getFirst().consumers().getFirst()).containsEntry("id", STREAM_ID);
    assertThat(result.client()).hasSize(1);
    assertThat(result.client().getFirst().jobType()).isEqualTo(JOB_TYPE);
    assertThat(result.client().getFirst().id().serverStreamId()).isEqualTo(STREAM_ID);
    assertThat(result.client().getFirst().id().localId()).isEqualTo(7);
    assertThat(result.client().getFirst().connectedTo()).containsExactly(0, 1);
    verify(getRequestedFor(urlEqualTo(JOB_STREAMS_PATH)));
  }

  @Test
  void fetchJobStreams_shouldThrowIOException_whenEndpointReturnsNon200(
      WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(get(JOB_STREAMS_PATH).willReturn(serverError().withBody("boom")));
    var client = newClient(wmRuntimeInfo.getHttpPort());

    assertThatThrownBy(client::fetchJobStreams)
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Gateway job-streams endpoint returned status 500")
        .hasMessageContaining(JOB_STREAMS_PATH);
    verify(getRequestedFor(urlEqualTo(JOB_STREAMS_PATH)));
  }

  @Test
  void fetchJobStreams_shouldPropagateParseError_whenEndpointReturnsMalformedJson(
      WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(get(JOB_STREAMS_PATH).willReturn(ok().withBody("{")));
    var client = newClient(wmRuntimeInfo.getHttpPort());

    assertThatThrownBy(client::fetchJobStreams).isInstanceOf(JsonProcessingException.class);
    verify(getRequestedFor(urlEqualTo(JOB_STREAMS_PATH)));
  }

  private GatewayJobStreamClient newClient(int monitoringPort) {
    CamundaClient camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(camundaClient.getConfiguration().getRestAddress()).thenReturn(URI.create(REST_ADDRESS));
    return new GatewayJobStreamClient(
        camundaClient, monitoringPort, ConnectorsObjectMapperSupplier.getCopy());
  }
}
