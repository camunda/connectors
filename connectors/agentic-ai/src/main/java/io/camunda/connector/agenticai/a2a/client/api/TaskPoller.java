/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.api;

import io.a2a.client.Client;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface TaskPoller {
  CompletableFuture<A2aSendMessageResult> poll(
      String taskId, Client client, Duration pollInterval, Duration timeout);
}
