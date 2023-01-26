/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.utils;

import com.google.api.client.http.HttpRequest;
import io.camunda.connector.graphql.model.GraphQLRequest;
import java.util.concurrent.TimeUnit;

public class Timeout {

  public static void setTimeout(GraphQLRequest request, HttpRequest httpRequest) {
    if (request.getConnectionTimeoutInSeconds() != null) {
      long connectionTimeout =
          TimeUnit.SECONDS.toMillis(Long.parseLong(request.getConnectionTimeoutInSeconds()));
      int intConnectionTimeout = Math.toIntExact(connectionTimeout);
      httpRequest.setConnectTimeout(intConnectionTimeout);
      httpRequest.setReadTimeout(intConnectionTimeout);
      httpRequest.setWriteTimeout(intConnectionTimeout);
    }
  }
}
