/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import io.camunda.connector.api.annotation.Secret;
import java.util.Objects;
import javax.validation.constraints.NotEmpty;

public class AuthenticationRequestData {
  @NotEmpty @Secret private String accessKey;
  @NotEmpty @Secret private String secretKey;

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(final String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(final String secretKey) {
    this.secretKey = secretKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AuthenticationRequestData that = (AuthenticationRequestData) o;
    return Objects.equals(accessKey, that.accessKey) && Objects.equals(secretKey, that.secretKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessKey, secretKey);
  }

  @Override
  public String toString() {
    return "AuthenticationRequestData{" + "accessKey=[REDACTED], " + "secretKey=[REDACTED]" + "}";
  }
}
