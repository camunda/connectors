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
package io.camunda.connector.http.client.model;

/**
 * PEM-encoded mutual TLS configuration. All fields are passed as dynamic values (process variables
 * or secrets), so they hold the certificate/key material directly rather than file paths.
 */
public record ClientTls(
    String clientCertificate,
    String clientPrivateKey,
    String privateKeyPassword,
    String trustedCertificate) {

  /** Client identity for mTLS: both the certificate chain and its private key must be present. */
  public boolean hasIdentity() {
    return isNotBlank(clientCertificate) && isNotBlank(clientPrivateKey);
  }

  /** Custom CA material to validate the server certificate. */
  public boolean hasTrust() {
    return isNotBlank(trustedCertificate);
  }

  public boolean isConfigured() {
    return hasIdentity() || hasTrust();
  }

  private static boolean isNotBlank(String s) {
    return s != null && !s.isBlank();
  }
}
