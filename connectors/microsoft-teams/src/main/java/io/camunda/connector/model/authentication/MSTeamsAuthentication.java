/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.authentication;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
import okhttp3.Request;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BearerAuthentication.class, name = "token"),
  @JsonSubTypes.Type(value = ClientSecretAuthentication.class, name = "clientCredentials"),
  @JsonSubTypes.Type(value = RefreshTokenAuthentication.class, name = "refresh")
})
public abstract class MSTeamsAuthentication {
  private transient String type;

  public abstract GraphServiceClient<Request> buildAndGetGraphServiceClient(
      GraphServiceClientSupplier clientSupplier);
}
