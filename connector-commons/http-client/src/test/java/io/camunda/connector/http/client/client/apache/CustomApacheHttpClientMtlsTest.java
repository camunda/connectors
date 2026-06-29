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
package io.camunda.connector.http.client.client.apache;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.mapper.ResponseMappers;
import io.camunda.connector.http.client.model.ClientTls;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies mutual TLS against a WireMock server that requires client authentication. Fixtures (a
 * self-signed server identity and a self-signed client identity) live under {@code
 * src/test/resources/mtls} and were generated with openssl/keytool.
 */
public class CustomApacheHttpClientMtlsTest {

  private static final String STORE_PASSWORD = "password";

  private final CustomApacheHttpClient httpClient = new CustomApacheHttpClient();

  private static WireMockServer server;
  private static String clientCertPem;
  private static String clientKeyPem;
  private static String serverCertPem;

  @BeforeAll
  static void setUp() throws Exception {
    clientCertPem = readResource("client.crt");
    clientKeyPem = readResource("client.key");
    serverCertPem = readResource("server.crt");

    server =
        new WireMockServer(
            options()
                .httpDisabled(true)
                .dynamicHttpsPort()
                // server identity presented to the client
                .keystorePath(resourcePath("server-keystore.p12"))
                .keystorePassword(STORE_PASSWORD)
                .keyManagerPassword(STORE_PASSWORD)
                .keystoreType("PKCS12")
                // require + validate the client certificate (the mTLS part)
                .needClientAuth(true)
                .trustStorePath(resourcePath("server-truststore.p12"))
                .trustStorePassword(STORE_PASSWORD)
                .trustStoreType("PKCS12"));
    server.start();
    server.stubFor(get("/secure").willReturn(ok().withBody("mtls-ok")));
  }

  @AfterAll
  static void tearDown() {
    server.stop();
  }

  @Test
  void shouldSucceed_whenClientPresentsCertificateAndTrustsServer() {
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(server.baseUrl() + "/secure");
    request.setClientTls(new ClientTls(clientCertPem, clientKeyPem, null, serverCertPem));

    var response = httpClient.execute(request, ResponseMappers.asString());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.entity()).isEqualTo("mtls-ok");
  }

  @Test
  void shouldFailHandshake_whenClientPresentsNoCertificate() {
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(server.baseUrl() + "/secure");
    // Only trust the server, present no client identity: the server rejects the handshake.
    request.setClientTls(new ClientTls(null, null, null, serverCertPem));

    assertThatThrownBy(() -> httpClient.execute(request, ResponseMappers.asString()))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void shouldRaiseTlsError_whenServerCertificateNotTrusted() {
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(server.baseUrl() + "/secure");
    // Present a client identity but no trusted certificate: the self-signed server is not in the
    // JVM default trust store, so the handshake fails on path validation. The incident must say so.
    request.setClientTls(new ClientTls(clientCertPem, clientKeyPem, null, null));

    assertThatThrownBy(() -> httpClient.execute(request, ResponseMappers.asString()))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", "SSL_HANDSHAKE_FAILED")
        .hasMessageContaining("TLS handshake failed")
        .hasMessageContaining("certification path");
  }

  private static String readResource(String name) throws Exception {
    return Files.readString(Paths.get(resourcePath(name)), StandardCharsets.UTF_8);
  }

  private static String resourcePath(String name) {
    try {
      return Path.of(CustomApacheHttpClientMtlsTest.class.getResource("/mtls/" + name).toURI())
          .toString();
    } catch (Exception e) {
      throw new IllegalStateException("Missing test resource /mtls/" + name, e);
    }
  }
}
