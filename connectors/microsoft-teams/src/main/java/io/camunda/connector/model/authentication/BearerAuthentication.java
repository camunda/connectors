/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.authentication;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import okhttp3.Request;

public class BearerAuthentication extends MSTeamsAuthentication {
  @Secret @NotBlank private String token;

  @Override
  public GraphServiceClient<Request> buildAndGetGraphServiceClient(
      GraphServiceClientSupplier clientSupplier) {
    return clientSupplier.buildAndGetGraphServiceClient(token);
  }

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BearerAuthentication that = (BearerAuthentication) o;
    return Objects.equals(token, that.token);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token);
  }

  @Override
  public String toString() {
    return "BearerAuthentication{" + "token='[secret_token]'" + "}";
  }
}
