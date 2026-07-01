/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record KafkaAuthentication(
    @TemplateProperty(
            group = "authentication",
            label = "Username",
            optional = true,
            tooltip = "The user must have permissions to produce messages to the topic.")
        String username,
    @TemplateProperty(group = "authentication", label = "Password", optional = true)
        String password) {
  @Override
  public String toString() {
    return "KafkaAuthentication{username='[REDACTED]', password='[REDACTED]'}";
  }
}
