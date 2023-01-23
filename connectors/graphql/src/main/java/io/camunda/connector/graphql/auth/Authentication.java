/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.auth;

import com.google.api.client.http.HttpHeaders;
import com.google.common.base.Objects;

public abstract class Authentication {

  private transient String type;

  public abstract void setHeaders(HttpHeaders headers);

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Authentication that = (Authentication) o;
    return Objects.equal(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type);
  }

  @Override
  public String toString() {
    return "Authentication{" + "type='" + type + '\'' + '}';
  }
}
