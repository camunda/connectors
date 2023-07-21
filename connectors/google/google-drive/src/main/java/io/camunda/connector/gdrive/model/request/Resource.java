/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class Resource {

  @NotNull private Type type;
  @NotEmpty private String name;
  private String parent;
  private JsonNode additionalGoogleDriveProperties;
  @Valid private Template template;

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

  public JsonNode getAdditionalGoogleDriveProperties() {
    return additionalGoogleDriveProperties;
  }

  public void setAdditionalGoogleDriveProperties(JsonNode additionalGoogleDriveProperties) {
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
