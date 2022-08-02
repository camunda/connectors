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

  private Authentication authentication;
  private FolderCreateParams folder;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(authentication, "Authentication");
    validateIfNotNull(authentication, validator);
    validator.require(folder, "Folder creation params");
    validateIfNotNull(folder, validator);
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    replaceSecretsIfNotNull(authentication, secretStore);
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final Authentication authentication) {
    this.authentication = authentication;
  }

  public FolderCreateParams getFolder() {
    return folder;
  }

  public void setFolder(final FolderCreateParams folder) {
    this.folder = folder;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GoogleDriveRequest that = (GoogleDriveRequest) o;
    return authentication.equals(that.authentication) && folder.equals(that.folder);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, folder);
  }

  @Override
  public String toString() {
    return "GoogleDriveRequest{" + "authentication=" + authentication + ", folder=" + folder + "}";
  }
}
