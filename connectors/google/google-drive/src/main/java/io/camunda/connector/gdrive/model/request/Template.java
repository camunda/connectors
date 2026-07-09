/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

public record Template(
    @TemplateProperty(
            id = "id",
            label = "Template ID",
            group = "operationDetails",
            feel = FeelMode.optional)
        @NotEmpty
        String id,
    @NestedProperties(addNestedPath = false) Variables variables) {}
