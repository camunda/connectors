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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches remote (broker-side) job-stream data from each Zeebe broker's monitoring endpoint ({@code
 * /actuator/jobstreams}). Operates in two modes:
 *
 * <ul>
 *   <li><b>Explicit addresses</b> (preferred for Docker/NAT'd environments): provide base URLs via
 *       {@code camunda.connector.broker.monitoring.addresses} (comma-separated, e.g. {@code
 *       http://localhost:9600,http://localhost:9601}). No topology request is made.
 *   <li><b>Topology discovery</b> (fallback): when no addresses are configured, broker hosts are
 *       resolved at query time via {@link CamundaClient#newTopologyRequest()}. The monitoring port
 *       defaults to {@code 9600} and can be overridden via {@code
 *       camunda.connector.broker.monitoring.port}.
 * </ul>
 *
 * <p>This client is used in standalone gateway deployments where the gateway's {@code
 * /actuator/jobstreams} endpoint cannot report broker-side streams (its {@code remote} field is
 * always empty). Enable with {@code camunda.connector.broker.monitoring.enabled=true}.
 */
public class BrokerJobStreamClient {

  private static final String JOB_STREAMS_PATH = "/actuator/jobstreams";

  // Explicit mode — null means use topology discovery instead
  private final List<URI> explicitBaseUris;
  // Topology mode — only used when explicitBaseUris is null
  private final CamundaClient camundaClient;
  private final int monitoringPort;

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  /** Topology-discovery mode: broker hosts are resolved at query time via the topology API. */
  public BrokerJobStreamClient(
      CamundaClient camundaClient, int monitoringPort, ObjectMapper objectMapper) {
    this.explicitBaseUris = null;
    this.camundaClient = camundaClient;
    this.monitoringPort = monitoringPort;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newHttpClient();
  }

  /**
   * Explicit-addresses mode: skip topology, query each of the given base URLs directly. Useful in
   * Docker or other NAT'd environments where topology-reported IPs are not reachable.
   */
  public BrokerJobStreamClient(List<URI> explicitBaseUris, ObjectMapper objectMapper) {
    this.explicitBaseUris = explicitBaseUris;
    this.camundaClient = null;
    this.monitoringPort = 0;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newHttpClient();
  }

  /**
   * Fetches and aggregates the {@code remote} job streams from all brokers. URIs are either taken
   * from the explicit address list or resolved from the Camunda topology.
   *
   * @return combined list of remote streams from all brokers
   * @throws IOException if any broker endpoint returns a non-200 status
   * @throws Exception if the topology request or any broker HTTP request fails
   */
  public List<RemoteJobStream> fetchRemoteStreams() throws Exception {
    List<URI> uris = resolveUris();
    List<RemoteJobStream> result = new ArrayList<>();
    for (URI uri : uris) {
      HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IOException(
            "Broker job-streams endpoint returned status " + response.statusCode() + ": " + uri);
      }
      result.addAll(objectMapper.readValue(response.body(), JobStreamsResponse.class).remote());
    }
    return result;
  }

  private List<URI> resolveUris() {
    if (explicitBaseUris != null) {
      return explicitBaseUris.stream().map(base -> base.resolve(JOB_STREAMS_PATH)).toList();
    }
    return camundaClient.newTopologyRequest().send().join().getBrokers().stream()
        .map(b -> URI.create("http://" + b.getHost() + ":" + monitoringPort + JOB_STREAMS_PATH))
        .toList();
  }
}
