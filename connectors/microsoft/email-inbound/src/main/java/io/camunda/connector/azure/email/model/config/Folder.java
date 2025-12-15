/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.email.model.config;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record Folder(
    @TemplateProperty(
            label = "Folder Name/Folder ID",
            feel = Property.FeelMode.optional,
            // TODO: Add link
            defaultValue = "inbox",
            description =
                "The folder name or folder ID. Folder names must be unique. Consider using the well-known folder IDs described here")
        @NotBlank
        String folderName,
    @TemplateProperty(
            label = "Specified folder ID",
            description = "Did you specify a folder ID?",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true",
            feel = Property.FeelMode.optional,
            tooltip =
                "To prevent name collisions, you can instead specify the folder ID. <a href='https://learn.microsoft.com/en-us/graph/api/resources/mailfolder?view=graph-rest-1.0#properties' target='_blank'> See the folder Properties described in the API</a> ")
        @FEEL
        boolean isFolderId) {}
