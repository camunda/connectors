/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "downloadBlob", label = "Download blob")
public record DownloadBlob(
    @TemplateProperty(
            label = "Blob Storage container",
            id = "downloadOperationContainer",
            group = "operation",
            tooltip = "A container acts as a directory that organizes a set of blobs.",
            feel = FeelMode.optional,
            binding =
                @TemplateProperty.PropertyBinding(
                    name = "operation.container")) // TODO can this bindings be removed?
        @NotBlank
        String container,
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
                "By default, only a reference to the document is returned. If this option is unchecked, the full content of the document is extracted and included in the response.",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true",
            binding = @TemplateProperty.PropertyBinding(name = "operation.asFile"))
        boolean asFile)
    implements BlobStorageOperation {}
