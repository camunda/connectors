/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(
    id = "file",
    label = "Create file from template",
    description = "Create a new file in Google Drive from a template",
    keywords = {
      "create file",
      "template",
      "google drive",
      "file from template",
      "document",
      "generate file"
    })
public record FileResource(
    @TemplateProperty(
            id = "fileName",
            binding = @TemplateProperty.PropertyBinding(name = "name"),
            label = "New resource name",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String name,
    @TemplateProperty(
            id = "fileParent",
            binding = @TemplateProperty.PropertyBinding(name = "parent"),
            label = "Parent folder ID",
            description =
                "Your resources will be created here. "
                    + "If left empty, a new resource will appear in the root folder",
            group = "operationDetails",
            optional = true,
            feel = FeelMode.optional)
        String parent,
    @Valid Template template)
    implements Resource {}
