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

public class GoogleDriveRequest implements ConnectorInput {

  private String token;
  private Resource resource;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(token, "Token");
    validateIfNotNull(resource, validator);
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    token = secretStore.replaceSecret(token);
    replaceSecretsIfNotNull(resource, secretStore);
  }

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  public Resource getResource() {
    return resource;
  }

  public void setResource(final Resource resource) {
    this.resource = resource;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GoogleDriveRequest request = (GoogleDriveRequest) o;
    return Objects.equals(token, request.token) && Objects.equals(resource, request.resource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, resource);
  }

  @Override
  public String toString() {
    return "GoogleDriveRequest{" + "token='[REDACTED]'" + ", resource=" + resource + "}";
  }
}
