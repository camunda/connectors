/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.authentication;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
import okhttp3.Request;

public abstract class MSTeamsAuthentication {
  private transient String type;

  public abstract GraphServiceClient<Request> buildAndGetGraphServiceClient(
      GraphServiceClientSupplier clientSupplier);
}
