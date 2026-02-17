/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "downloadObject", label = "Download object")
public record DownloadObject(
    @TemplateProperty(
            label = "GCP project",
            id = "downloadOperationProject",
            group = "operation",
            tooltip = "The project where the bucket is located.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.project"))
        @NotBlank
        String project,
    @TemplateProperty(
            label = "Object Storage bucket",
            id = "downloadOperationBucket",
            group = "operation",
            tooltip = "A bucket acts as a directory that organizes a set of objects.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.bucket"))
        @NotBlank
        String bucket,
    @TemplateProperty(
            label = "File name",
            id = "downloadOperationFileName",
            group = "operation",
            tooltip = "Specify the name of the document to be downloaded.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.fileName"))
        @NotBlank
        String fileName,
    @TemplateProperty(
            label = "Return document as reference",
            id = "downloadOperationAsFile",
            group = "operation",
            tooltip =
                "If checked, a Camunda document is created and its reference is returned\n"
                    + "If not checked, no document is created and the content is passed as is",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true",
            binding = @TemplateProperty.PropertyBinding(name = "operation.asDocument"))
        boolean asDocument)
    implements ObjectStorageOperation {}
