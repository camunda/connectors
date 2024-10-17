/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public record TaxonomyItem(
    @TemplateProperty(
            label = "Name",
            id = "name",
            description = "The name of the taxonomy item",
            binding = @TemplateProperty.PropertyBinding(name = "name"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String name,
    @TemplateProperty(
            label = "Prompt",
            id = "prompt",
            description = "The prompt for the taxonomy item",
            binding = @TemplateProperty.PropertyBinding(name = "prompt"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String prompt) {}
