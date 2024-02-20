/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SerializationType {
  @JsonProperty("json")
  JSON,
  @JsonProperty("avro")
  AVRO
}
