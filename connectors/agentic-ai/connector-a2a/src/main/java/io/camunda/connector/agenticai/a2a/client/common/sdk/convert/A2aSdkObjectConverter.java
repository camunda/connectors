/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.convert;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;

public interface A2aSdkObjectConverter {
  A2aMessage convert(Message message);

  A2aTask convert(Task task);
}
