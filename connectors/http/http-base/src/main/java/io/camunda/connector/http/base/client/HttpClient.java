/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.client;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;

public interface HttpClient {

  /**
   * Executes the given {@link HttpCommonRequest} and returns the result as a {@link
   * HttpCommonResult}.
   *
   * @param request the {@link HttpCommonRequest} to execute
   * @param remoteExecutionEnabled whether to use the internal Google Function to execute the
   *     request remotely
   * @return the result of the request as a {@link HttpCommonResult}
   */
  HttpCommonResult execute(HttpCommonRequest request, boolean remoteExecutionEnabled);

  /**
   * Executes the given {@link HttpCommonRequest} and returns the result as a {@link
   * HttpCommonResult}.
   *
   * @param request the {@link HttpCommonRequest} to execute
   * @return the result of the request as a {@link HttpCommonResult}
   */
  default HttpCommonResult execute(HttpCommonRequest request) {
    return execute(request, false);
  }
}
