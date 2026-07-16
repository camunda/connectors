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

@TemplateSubType(
    id = "upload",
    label = "Upload file",
    description = "Upload a file to Google Drive",
    keywords = {"upload", "upload file", "google drive", "file upload", "store file", "add file"})
public record UploadResource(
    @TemplateProperty(
            id = "uploadParent",
            binding = @TemplateProperty.PropertyBinding(name = "parent"),
            label = "Parent folder ID",
            tooltip =
                "Your resources will be created here. "
                    + "If left empty, a new resource will appear in the root folder.",
            group = "operationDetails",
            optional = true,
            feel = FeelMode.optional)
        String parent,
    @Valid UploadData uploadData)
    implements Resource {}
