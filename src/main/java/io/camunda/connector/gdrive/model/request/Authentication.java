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

import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class Authentication implements ConnectorInput {
  private String token;
  private String applicationName;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(token, "Token");
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    this.token = secretStore.replaceSecret(token);
  }

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  public String getApplicationName() {
    return applicationName;
  }

  public void setApplicationName(final String applicationName) {
    this.applicationName = applicationName;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Authentication that = (Authentication) o;
    return Objects.equals(token, that.token)
        && Objects.equals(applicationName, that.applicationName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, applicationName);
  }

  @Override
  public String toString() {
    return "Authentication{"
        + "token='"
        + token
        + "'"
        + ", applicationName='"
        + applicationName
        + "'}";
  }
}
