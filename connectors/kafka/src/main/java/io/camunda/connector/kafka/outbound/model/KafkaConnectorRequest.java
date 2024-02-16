/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import io.camunda.connector.kafka.model.Avro;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaTopic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record KafkaConnectorRequest(
    @Valid KafkaAuthentication authentication,
    @Valid @NotNull KafkaTopic topic,
    @Valid @NotNull KafkaMessage message,
    @Valid Avro avro,
    Map<String, Object> additionalProperties,
    Map<String, String> headers) {}
