/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.constraints.AssertFalse;
import java.util.Objects;

public class RabbitMqAuthentication {

  private RabbitMqAuthenticationType authType;
  @Secret private String userName;
  @Secret private String password;
  @Secret private String uri;

  @AssertFalse
  private boolean isAuthFieldsIsEmpty() {
    if (authType == RabbitMqAuthenticationType.uri) {
      return uri == null || uri.isBlank();
    }
    if (authType == RabbitMqAuthenticationType.credentials) {
      return userName == null || userName.isBlank() || password == null || password.isBlank();
    }
    return true;
  }

  public RabbitMqAuthenticationType getAuthType() {
    return authType;
  }

  public void setAuthType(final RabbitMqAuthenticationType authType) {
    this.authType = authType;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(final String userName) {
    this.userName = userName;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getUri() {
    return uri;
  }

  public void setUri(final String uri) {
    this.uri = uri;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RabbitMqAuthentication that = (RabbitMqAuthentication) o;
    return authType == that.authType
        && Objects.equals(userName, that.userName)
        && Objects.equals(password, that.password)
        && Objects.equals(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authType, userName, password, uri);
  }

  @Override
  public String toString() {
    return "RabbitMqAuthentication{" + "authType=" + authType + "}";
  }
}
