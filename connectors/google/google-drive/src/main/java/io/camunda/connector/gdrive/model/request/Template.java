/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import com.google.api.client.util.Key;
import io.camunda.connector.api.annotation.Secret;
import java.util.Objects;
import javax.validation.constraints.NotEmpty;

public class Template {
  @Key @NotEmpty @Secret private String id;
  @Key private Variables variables;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Variables getVariables() {
    return variables;
  }

  public void setVariables(final Variables variables) {
    this.variables = variables;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Template template = (Template) o;
    return Objects.equals(id, template.id) && Objects.equals(variables, template.variables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, variables);
  }

  @Override
  public String toString() {
    return "Template{" + "id='" + id + "'" + ", variables=" + variables + "}";
  }
}
