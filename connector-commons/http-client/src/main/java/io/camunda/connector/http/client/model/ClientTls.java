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
 *
 * <p>In mutual TLS <em>two</em> identities are proven during the handshake, not one. Ordinary TLS
 * only has the server prove who it is; in mTLS the client also presents a certificate. The fields
 * below split into the client's own identity ({@code clientCertificate} + {@code clientPrivateKey}
 * + {@code privateKeyPassword}) and the material the client uses to trust the server ({@code
 * trustedCertificate}).
 *
 * <p>This configuration operates at the transport layer and is independent of any HTTP-message
 * authentication (Bearer, OAuth, ...). The two can be combined — e.g. mTLS plus a bearer token on
 * the same request.
 *
 * <p>Supported combinations:
 *
 * <ul>
 *   <li><b>Identity only</b> — present a client certificate; validate the server against the JVM's
 *       default CA bundle. The typical "API requires mTLS but has a normal public certificate" case.
 *   <li><b>Trust only</b> — present no client certificate; trust a private/self-signed server CA.
 *       This is plain TLS against a custom CA rather than mTLS, but the same field handles it.
 *   <li><b>Both</b> — full mTLS against a private-CA server: present a client certificate and pin
 *       the server's CA.
 * </ul>
 *
 * @param clientCertificate PEM-encoded X.509 certificate (chain) the connector presents <em>to the
 *     server</em> as the client half of mTLS. The server validates it against its own trust store.
 *     Public material (it is sent over the wire). Optional intermediate CA certificates may follow
 *     the leaf certificate. Only takes effect together with {@code clientPrivateKey}.
 * @param clientPrivateKey PEM-encoded private key matching the public key in {@code
 *     clientCertificate}. The certificate is public and provable only by signing the handshake with
 *     this key, so this is <em>secret</em> material and should come from a secret rather than a
 *     plain process variable. Supports PKCS#1 ({@code BEGIN RSA PRIVATE KEY}), PKCS#8 ({@code BEGIN
 *     PRIVATE KEY}) and EC keys.
 * @param privateKeyPassword password that decrypts {@code clientPrivateKey} when it is an encrypted
 *     PEM ({@code BEGIN ENCRYPTED PRIVATE KEY}). Optional — leave {@code null}/empty for an
 *     unencrypted key. Not a TLS concept; purely local key-at-rest protection.
 * @param trustedCertificate PEM-encoded CA certificate(s) the client accepts the <em>server's</em>
 *     certificate against — the standard-TLS trust decision. Use it when the server presents a
 *     self-signed or private-CA certificate not in the JVM's default trust store. If {@code
 *     null}/empty, the JVM's built-in public CA bundle is used, as for a normal HTTPS call.
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
