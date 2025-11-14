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
package io.camunda.connector.http.client.model.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * mTLS client certificate authentication. Supports both keystore-based (for client certificate) and
 * truststore-based (for server certificate validation) authentication.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientCertificateAuthentication implements HttpAuthentication {

  public static final String TYPE = "clientCertificate";

  private String keystorePath;
  private String keystorePassword;
  private String keyPassword;
  private String truststorePath;
  private String truststorePassword;

  public String getKeystorePath() {
    return keystorePath;
  }

  public void setKeystorePath(String keystorePath) {
    this.keystorePath = keystorePath;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public void setKeystorePassword(String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  public String getKeyPassword() {
    return keyPassword;
  }

  public void setKeyPassword(String keyPassword) {
    this.keyPassword = keyPassword;
  }

  public String getTruststorePath() {
    return truststorePath;
  }

  public void setTruststorePath(String truststorePath) {
    this.truststorePath = truststorePath;
  }

  public String getTruststorePassword() {
    return truststorePassword;
  }

  public void setTruststorePassword(String truststorePassword) {
    this.truststorePassword = truststorePassword;
  }

  @Override
  public String toString() {
    return "ClientCertificateAuthentication{"
        + "keystorePath='"
        + keystorePath
        + '\''
        + ", truststorePath='"
        + truststorePath
        + '\''
        + '}';
  }
}
