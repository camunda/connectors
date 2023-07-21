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
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import okhttp3.Request;

public class ClientSecretAuthentication extends MSTeamsAuthentication {
  @Secret @NotBlank private String clientId;
  @Secret @NotBlank private String tenantId;
  @Secret @NotBlank private String clientSecret;

  @Override
  public GraphServiceClient<Request> buildAndGetGraphServiceClient(
      final GraphServiceClientSupplier clientSupplier) {
    return clientSupplier.buildAndGetGraphServiceClient(this);
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClientSecretAuthentication that = (ClientSecretAuthentication) o;
    return Objects.equals(clientId, that.clientId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(clientSecret, that.clientSecret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clientId, tenantId, clientSecret);
  }

  @Override
  public String toString() {
    return "ClientSecretAuthentication{"
        + "clientId='"
        + clientId
        + "'"
        + ", tenantId='"
        + tenantId
        + "'"
        + ", clientSecret='[client secret]'"
        + "}";
  }
}
