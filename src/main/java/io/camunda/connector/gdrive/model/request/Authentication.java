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
package io.camunda.connector.gdrive.model.request;

import com.google.api.client.util.Key;
import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

// TODO: requires refactoring when refresh token is implemented
public class Authentication implements ConnectorInput {

  @Key private String bearerToken;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(bearerToken, "Bearer token");
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    bearerToken = secretStore.replaceSecret(bearerToken);
  }

  public String getBearerToken() {
    return bearerToken;
  }

  public void setBearerToken(final String bearerToken) {
    this.bearerToken = bearerToken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Authentication that = (Authentication) o;
    return Objects.equals(bearerToken, that.bearerToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bearerToken);
  }

  @Override
  public String toString() {
    return "Authentication{" + "bearerToken=[REDACTED]}";
  }
}
