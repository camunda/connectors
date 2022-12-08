/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.authentication.MSTeamsAuthentication;
import io.camunda.connector.model.request.MSTeamsRequestData;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class MSTeamsRequest {

  @Valid @NotNull @Secret private MSTeamsAuthentication authentication;
  @Valid @NotNull @Secret private MSTeamsRequestData data;

  public Object invoke(final GraphServiceClientSupplier graphSupplier) {
    return data.invoke(authentication.buildAndGetGraphServiceClient(graphSupplier));
  }

  public MSTeamsAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final MSTeamsAuthentication authentication) {
    this.authentication = authentication;
  }

  public MSTeamsRequestData getData() {
    return data;
  }

  public void setData(final MSTeamsRequestData data) {
    this.data = data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MSTeamsRequest that = (MSTeamsRequest) o;
    return Objects.equals(authentication, that.authentication) && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, data);
  }

  @Override
  public String toString() {
    return "MSTeamsRequest{" + "authentication=" + authentication + ", data=" + data + "}";
  }
}
