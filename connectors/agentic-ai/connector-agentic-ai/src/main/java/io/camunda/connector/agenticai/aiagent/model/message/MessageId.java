/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;

/**
 * A {@link Message}'s self-generated id. Serializes as a bare JSON string, matching the persisted
 * format used before this type existed. Accepts any valid UUID on parse, not only version 7, so ids
 * persisted before this type was introduced keep deserializing unchanged.
 */
public record MessageId(@JsonValue UUID value) {

  @Override
  public String toString() {
    return value.toString();
  }

  @JsonCreator
  public static MessageId of(String value) {
    return new MessageId(UUID.fromString(value));
  }

  public static MessageId of(UUID value) {
    return new MessageId(value);
  }
}
