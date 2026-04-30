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

/**
 * Fetches job-stream data from the Zeebe gateway monitoring endpoint ({@code
 * /actuator/jobstreams}). The monitoring port defaults to {@code 9600} but can be overridden via
 * {@code camunda.connector.gateway.monitoring-port}.
 *
 * <p>The gateway host is inferred from the REST address exposed by {@link CamundaClient}.
 */
public class GatewayJobStreamClient {

  private static final String JOB_STREAMS_PATH = "/actuator/jobstreams";

  private final URI monitoringBaseUri;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public GatewayJobStreamClient(
      CamundaClient camundaClient, int monitoringPort, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newHttpClient();

    URI restAddress = camundaClient.getConfiguration().getRestAddress();
    this.monitoringBaseUri =
        URI.create(restAddress.getScheme() + "://" + restAddress.getHost() + ":" + monitoringPort);
  }

  /**
   * Fetches the current job-stream state from the gateway monitoring endpoint.
   *
   * @return the parsed {@link JobStreamsResponse}
   * @throws IOException if the endpoint returns a non-200 status
   * @throws Exception if the request fails for any other reason (network error, parse error, etc.)
   */
  public JobStreamsResponse fetchJobStreams() throws Exception {
    URI uri = monitoringBaseUri.resolve(JOB_STREAMS_PATH);
    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException(
          "Gateway job-streams endpoint returned status " + response.statusCode() + ": " + uri);
    }
    return objectMapper.readValue(response.body(), JobStreamsResponse.class);
  }
}
