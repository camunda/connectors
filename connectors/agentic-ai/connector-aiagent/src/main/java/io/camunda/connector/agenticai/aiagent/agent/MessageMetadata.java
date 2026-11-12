/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/** The metadata every message created by the agent carries. */
final class MessageMetadata {

  private MessageMetadata() {}

  static Map<String, Object> defaults() {
    return Map.of("timestamp", ZonedDateTime.now(ZoneId.systemDefault()));
  }
}
