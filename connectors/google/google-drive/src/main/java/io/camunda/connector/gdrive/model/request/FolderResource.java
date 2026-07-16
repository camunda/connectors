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
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(
    id = "folder",
    label = "Create folder",
    description = "Create a new folder in Google Drive",
    keywords = {"create folder", "new folder", "google drive", "folder", "directory"})
public record FolderResource(
    @TemplateProperty(
            id = "folderName",
            binding = @TemplateProperty.PropertyBinding(name = "name"),
            label = "New resource name",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String name,
    @TemplateProperty(
            id = "folderParent",
            binding = @TemplateProperty.PropertyBinding(name = "parent"),
            label = "Parent folder ID",
            tooltip =
                "Your resources will be created here. "
                    + "If left empty, a new resource will appear in the root folder.",
            group = "operationDetails",
            optional = true,
            feel = FeelMode.optional)
        String parent,
    @TemplateProperty(
            id = "additionalGoogleDriveProperties",
            label = "Additional properties or metadata",
            group = "operationDetails",
            feel = FeelMode.required,
            optional = true)
        JsonNode additionalGoogleDriveProperties)
    implements Resource {}
