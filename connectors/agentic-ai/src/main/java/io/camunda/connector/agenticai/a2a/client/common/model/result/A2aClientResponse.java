/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.model.result;

import io.camunda.connector.agenticai.model.AgenticAiRecord;
import javax.annotation.Nullable;

@AgenticAiRecord
public record A2aClientResponse(
    A2aResult result,
    @Nullable PushNotificationData pushNotificationData,
    @Nullable PollingData pollingData) {

  public static A2aClientResponseBuilder builder() {
    return new A2aClientResponseBuilder();
  }

  public record PushNotificationData(String token) {}

  public record PollingData(
      /*
       Task or message identifier or a unique generated identifier for the agent card
       and is used primarily for message correlation by the A2A inbound polling connector.
      */
      String id) {}
}
