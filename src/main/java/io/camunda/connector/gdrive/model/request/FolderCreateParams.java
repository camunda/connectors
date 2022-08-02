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
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class FolderCreateParams implements ConnectorInput {
  private String name;
  private String parent;
  private String additionalProperties;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(name, "Folder name");
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(final String parent) {
    this.parent = parent;
  }

  public String getAdditionalProperties() {
    return additionalProperties;
  }

  public void setAdditionalProperties(final String additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FolderCreateParams that = (FolderCreateParams) o;
    return name.equals(that.name)
        && Objects.equals(parent, that.parent)
        && Objects.equals(additionalProperties, that.additionalProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, parent, additionalProperties);
  }

  @Override
  public String toString() {
    return "FolderCreateParams{"
        + "name='"
        + name
        + "'"
        + ", parent='"
        + parent
        + "'"
        + ", additionalProperties='"
        + additionalProperties
        + "'}";
  }
}
