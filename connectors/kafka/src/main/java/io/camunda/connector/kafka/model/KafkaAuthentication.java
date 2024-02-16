/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

public record KafkaAuthentication(String username, String password) {
  @Override
  public String toString() {
    return "KafkaAuthentication{username='[REDACTED]', password='[REDACTED]'}";
  }
}
