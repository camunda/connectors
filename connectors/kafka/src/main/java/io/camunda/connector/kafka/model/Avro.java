/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.camunda.connector.api.annotation.FEEL;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record Avro(@FEEL @NotNull @NotBlank String schema) {}
