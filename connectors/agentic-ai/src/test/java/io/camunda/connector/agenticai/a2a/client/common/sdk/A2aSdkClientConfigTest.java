package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

class A2aSdkClientConfigTest {

  @Test
  void shouldThrowExceptionWhenBothBlockingAndPushNotificationsEnabled() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new A2aSdkClientConfig(
                    10,
                    true,
                    new A2aSdkClientConfig.PushNotificationConfig(
                        "http://example.com", List.of("Bearer"), null)));
    assertEquals("Cannot enable both blocking and push notifications.", exception.getMessage());
  }
}
