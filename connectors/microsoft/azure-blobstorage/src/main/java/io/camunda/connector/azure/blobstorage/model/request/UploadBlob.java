/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "uploadBlob", label = "Upload blob")
public record UploadBlob(
    @TemplateProperty(
            label = "Blob Storage container",
            id = "container",
            group = "operation",
            tooltip = "A container acts as a directory that organizes a set of blobs.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.container"))
        @NotBlank
        String container,
    @TemplateProperty(
            label = "Document",
            group = "operation",
            id = "uploadOperationDocument",
            tooltip = "Document to be uploaded to Azure Blob Storage.",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.required,
            binding = @TemplateProperty.PropertyBinding(name = "operation.document"))
        @NotNull
        Document document,
    @TemplateProperty(
            label = "Document file name",
            id = "uploadOperationFileName",
            group = "additionalProperties",
            tooltip =
                "By default, the file's metadata name is used unless a custom name is specified.",
            optional = true,
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.fileName"))
        String fileName,
    @TemplateProperty(
            label = "Timeout (in seconds)",
            group = "additionalProperties",
            id = "timeout",
            type = TemplateProperty.PropertyType.Number,
            defaultValue = "30",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            binding = @TemplateProperty.PropertyBinding(name = "operation.timeout"))
        @Min(1)
        @NotNull
        Integer timeout)
    implements BlobStorageOperation {}
