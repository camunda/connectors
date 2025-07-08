/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import io.camunda.connector.generator.dsl.Property;
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
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.project"))
        @NotBlank
        String project,
    @TemplateProperty(
            label = "Object Storage bucket",
            id = "downloadOperationBucket",
            group = "operation",
            tooltip = "A bucket acts as a directory that organizes a set of objects.",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.bucket"))
        @NotBlank
        String bucket,
    @TemplateProperty(
            label = "File name",
            id = "downloadOperationFileName",
            group = "operation",
            tooltip = "Specify the name of the document to be downloaded.",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.fileName"))
        @NotBlank
        String fileName,
    @TemplateProperty(
            label = "Return document as reference",
            id = "downloadOperationAsFile",
            group = "operation",
            tooltip =
                "By default, only a reference to the document is returned. If this option is unchecked, the full content of the document is extracted and included in the response.",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true",
            binding = @TemplateProperty.PropertyBinding(name = "operation.asFile"))
        boolean asFile)
    implements ObjectStorageOperation {}
