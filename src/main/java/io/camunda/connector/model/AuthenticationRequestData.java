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
package io.camunda.connector.model;

import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class AuthenticationRequestData implements ConnectorInput {
  private String accessKey;
  private String secretKey;

  @Override
  public void validateWith(Validator validator) {
    validator.require(accessKey, "access key");
    validator.require(secretKey, "secret key");
  }

  @Override
  public void replaceSecrets(SecretStore secretStore) {
    accessKey = secretStore.replaceSecret(accessKey);
    secretKey = secretStore.replaceSecret(secretKey);
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AuthenticationRequestData that = (AuthenticationRequestData) o;
    return accessKey.equals(that.accessKey) && secretKey.equals(that.secretKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessKey, secretKey);
  }

  @Override
  public String toString() {
    return "AuthenticationRequestData{" + "accessKey=[REDACTED]" + ", secretKey=[REDACTED]" + '}';
  }
}
