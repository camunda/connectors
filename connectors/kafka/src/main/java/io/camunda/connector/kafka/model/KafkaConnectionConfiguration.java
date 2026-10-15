/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

@Configuration(
    id = "io.camunda.connectors:kafka-connection:1",
    version = 1,
    name = "Kafka Connection")
public record KafkaConnectionConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "Bootstrap servers",
            placeholder = "broker1:9093,broker2:9093",
            tooltip =
                "Bootstrap server(s), comma-delimited if there are multiple. Uses SASL_SSL with the PLAIN mechanism.")
        String bootstrapServers,
    @NotBlank @TemplateProperty(group = "authentication", label = "Username", secret = true)
        String username,
    @NotBlank @TemplateProperty(group = "authentication", label = "Password", secret = true)
        String password) {

  public KafkaAuthentication toAuthentication() {
    return new KafkaAuthentication(username, password);
  }

  @Override
  public String toString() {
    return "KafkaConnectionConfiguration{bootstrapServers='[REDACTED]', username='[REDACTED]', password='[REDACTED]'}";
  }
}
