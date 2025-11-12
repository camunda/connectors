/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.api;

import io.camunda.connector.agenticai.a2a.client.model.A2aRequest;
import io.camunda.connector.agenticai.a2a.common.model.result.A2aResult;

public interface A2aRequestHandler {
  A2aResult handle(A2aRequest request);
}
