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
package io.camunda.connector.runtime.managedcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.camunda.client.CamundaClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RestManagedScriptControlPlaneTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void retriesTransientMissingArtifactAfterDefinitionIsLeased() throws Exception {
    final var artifactRequests = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v2/managed-script-definitions/activation",
        exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "definitions": [{
                    "managedScriptDefinitionKey": 1,
                    "status": "PENDING",
                    "revision": 1,
                    "resourceKey": 123,
                    "resourceName": "sum.js",
                    "artifactDigest": "0000000000000000000000000000000000000000000000000000000000000000",
                    "language": "javascript",
                    "runtime": "nodejs22",
                    "provider": "fake",
                    "leaseToken": "lease",
                    "providerOperationId": "",
                    "providerDeploymentId": "",
                    "tenantId": "<default>"
                  }]
                }
                """));
    server.createContext(
        "/v2/resources/123/content/binary",
        exchange -> {
          if (artifactRequests.incrementAndGet() == 1) {
            respond(exchange, 404, "not indexed yet");
          } else {
            respond(exchange, 200, "export function execute() { return {}; }");
          }
        });
    server.start();
    final var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getRestAddress())
        .thenReturn(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    when(client.getConfiguration().getDefaultTenantId()).thenReturn("<default>");
    final var controlPlane = new RestManagedScriptControlPlane("fake", new ObjectMapper());

    final var deployments =
        controlPlane.acquireDeployments(client, "default", "worker", 1, Duration.ofMinutes(2));

    assertThat(artifactRequests).hasValue(2);
    assertThat(deployments)
        .singleElement()
        .satisfies(
            deployment ->
                assertThat(deployment.artifact())
                    .containsExactly(
                        "export function execute() { return {}; }"
                            .getBytes(StandardCharsets.UTF_8)));
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    final var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (var response = exchange.getResponseBody()) {
      response.write(bytes);
    }
  }
}
