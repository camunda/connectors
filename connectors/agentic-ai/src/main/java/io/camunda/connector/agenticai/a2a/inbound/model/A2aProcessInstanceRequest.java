/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.model;

import io.camunda.connector.api.annotation.FEEL;

public record A2aProcessInstanceRequest(A2aProcessInstanceRequestData data) {
  public record A2aProcessInstanceRequestData(@FEEL String taskId) {}
}
