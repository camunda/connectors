/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.api;

import io.a2a.client.ClientEvent;
import io.a2a.spec.Task;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;

public interface A2aSendMessageResponseHandler {
  A2aSendMessageResult handleClientEvent(ClientEvent clientEvent);

  A2aTask handleTask(Task task);
}
