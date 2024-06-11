/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.client;

import io.camunda.connector.http.base.model.HttpCommonRequest;

public interface RequestFactory<T> {

  /**
   * Create a request from a {@link HttpCommonRequest}. This method is used to convert a domain
   * model to a request object that can be executed by the HTTP client of your choice.
   *
   * @param request the domain model
   * @return the request object
   */
  T createHttpRequest(HttpCommonRequest request);
}
