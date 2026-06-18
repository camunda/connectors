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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.model.ClientTls;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClientTlsFactoryTest {

  @Test
  void buildsSslContextFromPemCertificateAndKey() throws Exception {
    var tls =
        new ClientTls(readPem("client.crt"), readPem("client.key"), null, readPem("server.crt"));

    var sslContext = ClientTlsFactory.create(tls);

    assertThat(sslContext).isNotNull();
    assertThat(sslContext.getProtocol()).isEqualTo("TLS");
    // A usable engine proves the PEM identity + trust material were parsed and wired in.
    assertThat(sslContext.createSSLEngine()).isNotNull();
  }

  @Test
  void buildsSslContextFromPemTrustMaterialOnly() throws Exception {
    var tls = new ClientTls(null, null, null, readPem("server.crt"));

    assertThat(ClientTlsFactory.create(tls)).isNotNull();
  }

  @Test
  void failsWithConnectorInputExceptionOnMalformedPem() {
    var tls =
        new ClientTls(
            "-----BEGIN CERTIFICATE-----\nnot-a-real-cert\n-----END CERTIFICATE-----",
            "-----BEGIN PRIVATE KEY-----\nnope\n-----END PRIVATE KEY-----",
            null,
            null);

    assertThatThrownBy(() -> ClientTlsFactory.create(tls))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("mTLS");
  }

  @Test
  void treatsBlankPrivateKeyPasswordAsAbsent() throws Exception {
    // The key fixture is unencrypted; a blank password (empty form field) must not be applied.
    var tls =
        new ClientTls(readPem("client.crt"), readPem("client.key"), "", readPem("server.crt"));

    assertThat(ClientTlsFactory.create(tls)).isNotNull();
  }

  @Test
  void failsFastOnPartialIdentity() throws Exception {
    var certWithoutKey = new ClientTls(readPem("client.crt"), null, null, null);
    var keyWithoutCert = new ClientTls(null, readPem("client.key"), null, null);

    assertThatThrownBy(() -> ClientTlsFactory.create(certWithoutKey))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("client certificate and a private key");
    assertThatThrownBy(() -> ClientTlsFactory.create(keyWithoutCert))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("client certificate and a private key");
  }

  private static String readPem(String name) throws Exception {
    return Files.readString(
        Path.of(ClientTlsFactoryTest.class.getResource("/mtls/" + name).toURI()),
        StandardCharsets.UTF_8);
  }
}
