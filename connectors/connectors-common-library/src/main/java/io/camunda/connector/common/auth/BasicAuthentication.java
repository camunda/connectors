/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.auth;

import com.google.api.client.http.HttpHeaders;
import com.google.common.base.Objects;
import io.camunda.connector.api.annotation.Secret;
import javax.validation.constraints.NotEmpty;

public class BasicAuthentication extends Authentication {

  @NotEmpty @Secret private String username;
  @NotEmpty @Secret private String password;

  @Override
  public void setHeaders(final HttpHeaders headers) {
    headers.setBasicAuthentication(username, password);
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    BasicAuthentication that = (BasicAuthentication) o;
    return Objects.equal(username, that.username) && Objects.equal(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), username, password);
  }

  @Override
  public String toString() {
    return "BasicAuthentication {"
        + "username='[REDACTED]'"
        + ", password='[REDACTED]'"
        + "}; Super: "
        + super.toString();
  }
}
