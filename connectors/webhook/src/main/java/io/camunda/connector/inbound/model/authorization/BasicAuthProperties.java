/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model.authorization;

import java.util.Objects;

public class BasicAuthProperties {
  private String password;
  private String username;

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final BasicAuthProperties that)) {
      return false;
    }
    return Objects.equals(password, that.password) && Objects.equals(username, that.username);
  }

  @Override
  public int hashCode() {
    return Objects.hash(password, username);
  }

  @Override
  public String toString() {
    return "BasicAuthProperties{" + "password='[REDACTED]'" + ", username='[REDACTED]'" + "}";
  }
}
