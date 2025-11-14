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

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.model.auth.ClientCertificateAuthentication;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLContext;
import org.apache.hc.core5.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper class for building SSLContext for mTLS authentication. */
public class MtlsSSLContextBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(MtlsSSLContextBuilder.class);

  /**
   * Builds an SSLContext from the provided client certificate authentication configuration.
   *
   * @param auth the client certificate authentication configuration
   * @return the configured SSLContext
   * @throws ConnectorException if the SSLContext cannot be built
   */
  public static SSLContext buildSSLContext(ClientCertificateAuthentication auth) {
    try {
      var sslContextBuilder = SSLContexts.custom();

      // Load keystore (client certificate)
      if (auth.getKeystorePath() != null && !auth.getKeystorePath().isEmpty()) {
        KeyStore keyStore = loadKeyStore(auth.getKeystorePath(), auth.getKeystorePassword());
        char[] keyPassword =
            auth.getKeyPassword() != null ? auth.getKeyPassword().toCharArray() : null;
        sslContextBuilder.loadKeyMaterial(keyStore, keyPassword);
        LOG.debug("Loaded client keystore from: {}", auth.getKeystorePath());
      }

      // Load truststore (server certificate validation)
      if (auth.getTruststorePath() != null && !auth.getTruststorePath().isEmpty()) {
        KeyStore trustStore = loadKeyStore(auth.getTruststorePath(), auth.getTruststorePassword());
        sslContextBuilder.loadTrustMaterial(trustStore, null);
        LOG.debug("Loaded truststore from: {}", auth.getTruststorePath());
      }

      return sslContextBuilder.build();
    } catch (NoSuchAlgorithmException
        | KeyStoreException
        | UnrecoverableKeyException
        | java.security.KeyManagementException e) {
      throw new ConnectorException(
          "MTLS_CONFIG_ERROR", "Failed to configure mTLS: " + e.getMessage(), e);
    }
  }

  private static KeyStore loadKeyStore(String path, String password)
      throws ConnectorException, KeyStoreException {
    try {
      // Try PKCS12 first (most common format)
      return loadKeyStoreWithType(path, password, "PKCS12");
    } catch (Exception e) {
      LOG.debug("Failed to load keystore as PKCS12, trying JKS format", e);
      try {
        return loadKeyStoreWithType(path, password, "JKS");
      } catch (Exception jksException) {
        throw new ConnectorException(
            "MTLS_KEYSTORE_ERROR",
            "Failed to load keystore from "
                + path
                + ". Tried both PKCS12 and JKS formats: "
                + jksException.getMessage(),
            jksException);
      }
    }
  }

  private static KeyStore loadKeyStoreWithType(String path, String password, String type)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    KeyStore keyStore = KeyStore.getInstance(type);
    char[] passwordChars = password != null ? password.toCharArray() : null;
    try (FileInputStream fis = new FileInputStream(path)) {
      keyStore.load(fis, passwordChars);
    }
    return keyStore;
  }
}
