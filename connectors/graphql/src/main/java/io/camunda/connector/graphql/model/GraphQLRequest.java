/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.graphql.auth.Authentication;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class GraphQLRequest {

  @NotBlank @Secret private String method;

  @NotBlank
  @Pattern(regexp = "^(http://|https://|secrets).*$")
  @Secret
  private String url;

  @Valid @Secret private Authentication authentication;
  @NotBlank @Secret private String query;
  @Secret private Object variables;

  @Pattern(regexp = "^([0-9]*$)|(secrets.*$)")
  @Secret
  private String connectionTimeoutInSeconds;

  public boolean hasAuthentication() {
    return authentication != null;
  }

  public boolean hasQuery() {
    return query != null;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
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

  public String getConnectionTimeoutInSeconds() {
    return connectionTimeoutInSeconds;
  }

  public void setConnectionTimeoutInSeconds(String connectionTimeoutInSeconds) {
    this.connectionTimeoutInSeconds = connectionTimeoutInSeconds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GraphQLRequest that = (GraphQLRequest) o;
    return method.equals(that.method)
        && url.equals(that.url)
        && authentication.equals(that.authentication)
        && query.equals(that.query)
        && Objects.equals(variables, that.variables)
        && Objects.equals(connectionTimeoutInSeconds, that.connectionTimeoutInSeconds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, url, authentication, query, variables, connectionTimeoutInSeconds);
  }

  @Override
  public String toString() {
    return "GraphQLRequest{"
        + "method='"
        + method
        + '\''
        + ", url='"
        + url
        + '\''
        + ", authentication="
        + authentication
        + ", query="
        + query
        + ", variables="
        + variables
        + ", connectionTimeoutInSeconds='"
        + connectionTimeoutInSeconds
        + '\''
        + '}';
  }
}
