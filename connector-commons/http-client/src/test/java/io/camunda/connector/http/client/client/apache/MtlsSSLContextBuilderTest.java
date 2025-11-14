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

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.model.auth.ClientCertificateAuthentication;
import java.io.File;
import java.io.IOException;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MtlsSSLContextBuilderTest {

  @TempDir File tempDir;

  private String keystorePath;
  private String truststorePath;

  @BeforeEach
  void setUp() throws Exception {
    keystorePath = createTestKeystore("keystore.p12");
    truststorePath = createTestKeystore("truststore.p12");
  }

  @Test
  void shouldBuildSSLContextWithKeystore() {
    // Given
    var auth = new ClientCertificateAuthentication();
    auth.setKeystorePath(keystorePath);
    auth.setKeystorePassword("changeit");
    auth.setKeyPassword("changeit");

    // When
    SSLContext sslContext = MtlsSSLContextBuilder.buildSSLContext(auth);

    // Then
    assertThat(sslContext).isNotNull();
    assertThat(sslContext.getProtocol()).isEqualTo("TLS");
  }

  @Test
  void shouldBuildSSLContextWithTruststore() {
    // Given
    var auth = new ClientCertificateAuthentication();
    auth.setTruststorePath(truststorePath);
    auth.setTruststorePassword("changeit");

    // When
    SSLContext sslContext = MtlsSSLContextBuilder.buildSSLContext(auth);

    // Then
    assertThat(sslContext).isNotNull();
    assertThat(sslContext.getProtocol()).isEqualTo("TLS");
  }

  @Test
  void shouldBuildSSLContextWithBothKeystoreAndTruststore() {
    // Given
    var auth = new ClientCertificateAuthentication();
    auth.setKeystorePath(keystorePath);
    auth.setKeystorePassword("changeit");
    auth.setKeyPassword("changeit");
    auth.setTruststorePath(truststorePath);
    auth.setTruststorePassword("changeit");

    // When
    SSLContext sslContext = MtlsSSLContextBuilder.buildSSLContext(auth);

    // Then
    assertThat(sslContext).isNotNull();
    assertThat(sslContext.getProtocol()).isEqualTo("TLS");
  }

  @Test
  void shouldThrowExceptionWhenKeystoreFileNotFound() {
    // Given
    var auth = new ClientCertificateAuthentication();
    auth.setKeystorePath("/nonexistent/keystore.p12");
    auth.setKeystorePassword("changeit");

    // When/Then
    assertThatThrownBy(() -> MtlsSSLContextBuilder.buildSSLContext(auth))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Failed to load keystore");
  }

  @Test
  void shouldThrowExceptionWhenKeystorePasswordIncorrect() {
    // Given
    var auth = new ClientCertificateAuthentication();
    auth.setKeystorePath(keystorePath);
    auth.setKeystorePassword("wrongpassword");

    // When/Then
    assertThatThrownBy(() -> MtlsSSLContextBuilder.buildSSLContext(auth))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Failed to load keystore");
  }

  private String createTestKeystore(String filename) throws Exception {
    File keystoreFile = new File(tempDir, filename);

    // Use keytool to generate a test keystore
    ProcessBuilder pb =
        new ProcessBuilder(
            "keytool",
            "-genkeypair",
            "-alias",
            "test-key",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-storetype",
            "PKCS12",
            "-keystore",
            keystoreFile.getAbsolutePath(),
            "-storepass",
            "changeit",
            "-keypass",
            "changeit",
            "-validity",
            "365",
            "-dname",
            "CN=Test Certificate, O=Camunda, C=DE");

    Process process = pb.start();
    int exitCode = process.waitFor();

    if (exitCode != 0) {
      throw new IOException(
          "Failed to create test keystore: " + new String(process.getErrorStream().readAllBytes()));
    }

    return keystoreFile.getAbsolutePath();
  }
}
