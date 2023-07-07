/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.model;

import io.camunda.connector.common.model.CommonRequest;
import java.util.Objects;
import javax.validation.constraints.NotBlank;

public class GraphQLRequest extends CommonRequest {

  @NotBlank private String query;
  private Object variables;

  public boolean hasQuery() {
    return query != null;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public Object getVariables() {
    return variables;
  }

  public void setVariables(Object variables) {
    this.variables = variables;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GraphQLRequest that = (GraphQLRequest) o;
    return query.equals(that.query) && Objects.equals(variables, that.variables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(query, variables);
  }

  @Override
  public String toString() {
    return "GraphQLRequest{" + "query='" + query + '\'' + ", variables=" + variables + '}';
  }
}
