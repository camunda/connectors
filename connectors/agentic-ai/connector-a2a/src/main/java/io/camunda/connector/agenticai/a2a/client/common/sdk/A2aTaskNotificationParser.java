/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;

/** Parses the body of an A2A push notification into a task. */
public interface A2aTaskNotificationParser {

  /**
   * Parses the notification body.
   *
   * @throws IllegalArgumentException if the payload is not a valid A2A 1.0 stream response that
   *     contains a task
   */
  A2aTask parse(byte[] payload);
}
