/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.operations;

import io.camunda.connector.http.base.HttpService;
import java.util.Map;

/**
 * Represents a generic operation for the Automation Anywhere Connector in Camunda. This interface
 * is the foundation for defining specific operations like adding work items or getting work items
 * from a queue.
 *
 * <p>It is a sealed interface, which means only specified classes can implement it. Currently, it
 * is restricted to {@link AddWorkItemOperation} and {@link GetWorkItemOperation}.
 */
public sealed interface Operation permits AddWorkItemOperation, GetWorkItemOperation {
  /**
   * Executes the operation using the provided HTTP service, object mapper, and authentication
   * header. The specific behavior of this method is defined in the implementing classes.
   *
   * @param httpService The service used for executing HTTP requests.
   * @param authenticationHeader A map of headers for the HTTP request, specifically containing the
   *     Automation Anywhere token for authentication.
   * @return The result of the operation, which can vary based on the specific implementation.
   * @throws Exception if there is an error during the execution of the operation.
   */
  Object execute(final HttpService httpService, final Map<String, String> authenticationHeader)
      throws Exception;
}
