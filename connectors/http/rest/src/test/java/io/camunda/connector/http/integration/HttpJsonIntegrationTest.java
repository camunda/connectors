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
package io.camunda.connector.http.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.rest.HttpJsonFunction;
import io.camunda.connector.http.rest.model.HttpJsonRequest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HttpJsonIntegrationTest {

  private static WireMockServer wireMockServer;

  private final ObjectMapper objectMapper;

  public HttpJsonIntegrationTest() {
    this.objectMapper =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    ;
  }

  @BeforeEach
  public void setup() {
    wireMockServer =
        new WireMockServer(
            WireMockConfiguration.options()
                .httpsPort(8443) // Set the desired HTTPS port
                .trustStorePath(
                    "src/test/resources/ssl/servertruststore.jks") // Path to your truststore
                .trustStorePassword("secretPassword") // Truststore password
                .needClientAuth(
                    true) // TODO : if this is set it will throw java.net.SocketException: An
                // established connection was aborted by the software in your host machine
                .httpDisabled(true));
    wireMockServer.start();
  }

  @AfterEach
  public void teardown() {
    wireMockServer.stop();
  }

  @Test
  public void happyPathTest() throws Exception {
    wireMockServer.stubFor(
        get(urlEqualTo("/tlsTest"))
            .withPort(wireMockServer.httpsPort())
            .willReturn(
                ok().withHeader("Content-Type", "application/json")
                    .withBody("{\"response\": \"OK\"}")));

    HttpJsonFunction httpJsonFunction = new HttpJsonFunction();
    HttpJsonRequest httpJsonRequest = new HttpJsonRequest();
    httpJsonRequest.setUrl("https://localhost:8443/tlsTest");
    httpJsonRequest.setMethod("get");
    String variablesAsJSON = this.objectMapper.writeValueAsString(httpJsonRequest);
    OutboundConnectorContext outboundConnectorContext =
        OutboundConnectorContextBuilder.create().variables(variablesAsJSON).build();
    httpJsonFunction.execute(outboundConnectorContext);
  }
}
