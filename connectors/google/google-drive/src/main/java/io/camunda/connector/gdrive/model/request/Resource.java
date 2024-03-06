/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record Resource(
    @TemplateProperty(
            id = "type",
            label = "Operation",
            group = "operation",
            type = Dropdown,
            defaultValue = "folder",
            constraints = @PropertyConstraints(notEmpty = true),
            choices = {
              @DropdownPropertyChoice(label = "Create folder", value = "folder"),
              @DropdownPropertyChoice(label = "Create file from template", value = "file")
            })
        @NotNull
        Type type,
    @TemplateProperty(
            id = "name",
            label = "New resource name",
            group = "operationDetails",
            feel = FeelMode.optional)
        @NotEmpty
        String name,
    @TemplateProperty(
            id = "parent",
            label = "Parent folder ID",
            description =
                "Your resources will be created here. "
                    + "If left empty, a new resource will appear in the root folder",
            group = "operationDetails",
            optional = true,
            feel = FeelMode.optional)
        String parent,
    @TemplateProperty(
            id = "additionalGoogleDriveProperties",
            label = "Additional properties or metadata",
            group = "operationDetails",
            feel = FeelMode.required,
            optional = true,
            condition = @PropertyCondition(property = "resource.type", equals = "folder"))
        JsonNode additionalGoogleDriveProperties,
    @Valid Template template) {}
