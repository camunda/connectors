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
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "downloadBlob", label = "Download blob")
public record DownloadBlob(
    @TemplateProperty(
            label = "Blob Storage container",
            id = "downloadOperationContainer",
            group = "downloadBlob",
            tooltip = "Container from where an blob should be downloaded",
            feel = Property.FeelMode.optional,
            binding =
                @TemplateProperty.PropertyBinding(
                    name = "operation.container")) // TODO can this bindings be removed?
        @NotBlank
        String container,
    @TemplateProperty(
            label = "File name",
            id = "downloadOperationFileName",
            group = "downloadBlob",
            tooltip = "Filename of the blob which should be downloaded",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.fileName"))
        @NotBlank
        String fileName,
    @TemplateProperty(
            label = "Create document",
            id = "downloadOperationAsFile",
            group = "downloadBlob",
            tooltip =
                "If set to true, a document reference will be created. If set to false, the content will be extracted and provided inside the response.",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true",
            binding = @TemplateProperty.PropertyBinding(name = "operation.asFile"))
        boolean asFile)
    implements BlobStorageOperation {}
