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
import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class GoogleDriveRequest {

  @Key @Valid @NotNull @Secret private Authentication authentication;
  @Key @Valid @NotNull @Secret private Resource resource;

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final Authentication authentication) {
    this.authentication = authentication;
  }

  public Resource getResource() {
    return resource;
  }

  public void setResource(final Resource resource) {
    this.resource = resource;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    GoogleDriveRequest that = (GoogleDriveRequest) o;
    return Objects.equals(authentication, that.authentication)
        && Objects.equals(resource, that.resource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, resource);
  }

  @Override
  public String toString() {
    return "GoogleDriveRequest{"
        + "authentication="
        + authentication
        + ", resource="
        + resource
        + '}';
  }
}
