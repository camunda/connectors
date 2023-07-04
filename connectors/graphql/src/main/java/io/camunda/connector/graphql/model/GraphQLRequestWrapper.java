/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.model;

import io.camunda.connector.common.auth.Authentication;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GraphQLRequestWrapper {
  @Valid @NotNull private GraphQLRequest graphql;
  private Authentication authentication;

  public GraphQLRequest getGraphql() {
    return graphql;
  }

  public void setGraphql(GraphQLRequest graphql) {
    this.graphql = graphql;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GraphQLRequestWrapper that = (GraphQLRequestWrapper) o;
    return graphql.equals(that.graphql) && Objects.equals(authentication, that.authentication);
  }

  @Override
  public int hashCode() {
    return Objects.hash(graphql, authentication);
  }

  @Override
  public String toString() {
    return "GraphQLRequestWrapper{"
        + "graphql="
        + graphql
        + ", authentication="
        + authentication
        + '}';
  }
}
