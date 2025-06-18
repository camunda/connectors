/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.document.Document;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "uploadBlob", label = "Upload blob")
public record UploadBlob(
    @TemplateProperty(
            label = "Blob Storage container",
            id = "container",
            group = "uploadBlob",
            tooltip = "Container where an blob should be uploaded to",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.container"))
        @NotBlank
        String container,
    @TemplateProperty(
            label = "Document file name",
            id = "uploadOperationFileName",
            group = "uploadBlob",
            tooltip =
                "File name of the uploaded blob, if not given. The file name from the document metadata will be used",
            optional = true,
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.fileName"))
        String fileName,
    @TemplateProperty(
            label = "Document",
            group = "uploadBlob",
            id = "uploadOperationDocument",
            tooltip = "Document to be uploaded to Azure Blob Storage",
            type = TemplateProperty.PropertyType.String,
            feel = Property.FeelMode.required,
            binding = @TemplateProperty.PropertyBinding(name = "operation.document"))
        @NotNull
        Document document,
    @TemplateProperty(
            label = "Timeout",
            group = "additionalProperties",
            id = "timeout",
            tooltip = "Timeout for the upload in seconds.",
            type = TemplateProperty.PropertyType.Number,
            defaultValue = "30",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            binding = @TemplateProperty.PropertyBinding(name = "additionalProperties.timeout"))
        @Min(1)
        @NotNull
        Integer timeout)
    implements BlobStorageOperation {}
