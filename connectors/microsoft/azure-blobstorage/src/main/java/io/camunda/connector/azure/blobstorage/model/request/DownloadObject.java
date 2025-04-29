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

@TemplateSubType(id = "downloadObject", label = "Download object")
public record DownloadObject(
    @TemplateProperty(
            label = "Blob Storage container",
            id = "downloadActionContainer",
            group = "downloadObject",
            tooltip = "Container from where an object should be downloaded",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.container"))
        @NotBlank
        String container,
    @TemplateProperty(
            label = "File name",
            id = "downloadActionFileName",
            group = "downloadObject",
            tooltip = "Filename of the object which should be downloaded",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.fileName"))
        @NotBlank
        String fileName,
    @TemplateProperty(
            label = "Create document",
            id = "downloadActionAsFile",
            group = "downloadObject",
            tooltip =
                "If set to true, a document reference will be created. If set to false, the content will be extracted and provided inside the response.",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true",
            binding = @TemplateProperty.PropertyBinding(name = "action.asFile"))
        boolean asFile)
    implements BlobStorageAction {}
