/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;

public record Variables(
    @TemplateProperty(
            id = "variables",
            label = "Template variables",
            group = "operationDetails",
            feel = FeelMode.required,
            binding = @PropertyBinding(name = "variables"),
            condition = @PropertyCondition(property = "resource.type", equals = "file"))
        JsonNode requests) {}
