/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.operations;

import io.camunda.connector.automationanywhere.model.request.Configuration;
import io.camunda.connector.automationanywhere.model.request.operation.AddWorkItemOperationData;
import io.camunda.connector.automationanywhere.model.request.operation.GetWorkItemOperationData;
import io.camunda.connector.automationanywhere.model.request.operation.OperationData;

public class OperationFactory {

  private OperationFactory() {}

  public static Operation createOperation(
      final OperationData operationData, final Configuration configuration) {

    if (operationData instanceof AddWorkItemOperationData data) {
      return new AddWorkItemOperation(
          data.queueId(),
          data.data(),
          configuration.controlRoomUrl(),
          configuration.connectionTimeoutInSeconds());
    } else if (operationData instanceof GetWorkItemOperationData data) {
      return new GetWorkItemOperation(
          data.queueId(),
          data.workItemId(),
          configuration.controlRoomUrl(),
          configuration.connectionTimeoutInSeconds());
    } else {
      throw new IllegalArgumentException("Unsupported operation type");
    }
  }
}
