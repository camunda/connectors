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

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.model.ClientTls;
import javax.net.ssl.SSLContext;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.pem.util.PemUtils;

/**
 * Builds an {@link SSLContext} from PEM-encoded mutual TLS material. PEM parsing (PKCS1/PKCS8/EC,
 * encrypted or not) is delegated to ayza (sslcontext-kickstart), which the JDK's {@code KeyStore}
 * cannot do on its own.
 */
public final class ClientTlsFactory {

  private ClientTlsFactory() {}

  public static SSLContext create(ClientTls tls) {
    // Fail fast on a half-configured identity rather than silently dropping the client certificate.
    if (isNotBlank(tls.clientCertificate()) != isNotBlank(tls.clientPrivateKey())) {
      throw new ConnectorInputException(
          "mTLS client authentication requires both a client certificate and a private key; "
              + "only one was provided.");
    }
    try {
      var builder = SSLFactory.builder();
      if (tls.hasIdentity()) {
        // Treat a blank password (e.g. an empty form field) as no password.
        var password =
            isNotBlank(tls.privateKeyPassword()) ? tls.privateKeyPassword().toCharArray() : null;
        builder.withIdentityMaterial(
            PemUtils.parseIdentityMaterial(
                tls.clientCertificate(), tls.clientPrivateKey(), password));
      }
      if (tls.hasTrust()) {
        builder.withTrustMaterial(PemUtils.parseTrustMaterial(tls.trustedCertificate()));
      } else {
        // Keep validating the server against the JVM default CAs when only an identity is supplied.
        builder.withDefaultTrustMaterial();
      }
      return builder.build().getSslContext();
    } catch (Exception e) {
      // Keep the detail (which may derive from the key material) out of the incident; it stays on
      // the cause for the pod logs.
      throw new ConnectorInputException(
          "Failed to build SSL context for mTLS from the provided certificate material", e);
    }
  }

  private static boolean isNotBlank(String s) {
    return s != null && !s.isBlank();
  }
}
