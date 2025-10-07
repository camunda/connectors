/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.camunda.connector.agenticai.a2a.client.model.A2aClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aClientResult;

public interface A2aClientRequestHandler {
  A2aClientResult handle(A2aClientRequest request);
}
