/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aClientResponse;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aClientRequest;

public interface A2aClientRequestHandler {
  A2aClientResponse handle(A2aClientRequest request);
}
