/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import io.camunda.connector.agenticai.model.AgenticAiRecord;
import javax.annotation.Nullable;

@AgenticAiRecord
public record A2aSdkClientConfig(
    int historyLength, boolean blocking, @Nullable PushNotificationConfig pushNotificationConfig) {

  public A2aSdkClientConfig {
    if (blocking && pushNotificationConfig != null) {
      throw new IllegalArgumentException("Cannot enable both blocking and push notifications.");
    }
  }

  public record PushNotificationConfig(String url, String authScheme) {}
}
