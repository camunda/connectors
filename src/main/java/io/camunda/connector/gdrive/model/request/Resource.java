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
import com.google.api.services.drive.model.File;
import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class Resource implements ConnectorInput {
  @Key private Type type;
  @Key private String name;
  @Key private String parent;
  @Key private File additionalGoogleDriveProperties;
  @Key private Template template;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(type, "Resource type");
    validator.require(name, "Resource name");
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    name = secretStore.replaceSecret(name);
    parent = secretStore.replaceSecret(parent);
    replaceSecretsIfNotNull(template, secretStore);
  }

  public Type getType() {
    return type;
  }

  public void setType(final Type type) {
    this.type = type;
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

  public File getAdditionalGoogleDriveProperties() {
    return additionalGoogleDriveProperties;
  }

  public void setAdditionalGoogleDriveProperties(final File additionalGoogleDriveProperties) {
    this.additionalGoogleDriveProperties = additionalGoogleDriveProperties;
  }

  public Template getTemplate() {
    return template;
  }

  public void setTemplate(final Template template) {
    this.template = template;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Resource resource = (Resource) o;
    return type == resource.type
        && Objects.equals(name, resource.name)
        && Objects.equals(parent, resource.parent)
        && Objects.equals(additionalGoogleDriveProperties, resource.additionalGoogleDriveProperties)
        && Objects.equals(template, resource.template);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, name, parent, additionalGoogleDriveProperties, template);
  }

  @Override
  public String toString() {
    return "Resource{"
        + "type="
        + type
        + ", name='"
        + name
        + "'"
        + ", parent='"
        + parent
        + "'"
        + ", additionalGoogleDriveProperties="
        + additionalGoogleDriveProperties
        + ", template="
        + template
        + "}";
  }
}
