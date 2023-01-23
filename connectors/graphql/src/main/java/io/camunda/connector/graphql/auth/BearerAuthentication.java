/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.auth;

import com.google.api.client.http.HttpHeaders;
import com.google.common.base.Objects;
import io.camunda.connector.api.annotation.Secret;
import javax.validation.constraints.NotEmpty;

public class BearerAuthentication extends Authentication {

  @NotEmpty @Secret private String token;

  @Override
  public void setHeaders(final HttpHeaders headers) {
    headers.setAuthorization("Bearer " + token);
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    BearerAuthentication that = (BearerAuthentication) o;
    return Objects.equal(token, that.token);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), token);
  }

  @Override
  public String toString() {
    return "BearerAuthentication{" + "token='[REDACTED]'" + "}; Super: " + super.toString();
  }
}
