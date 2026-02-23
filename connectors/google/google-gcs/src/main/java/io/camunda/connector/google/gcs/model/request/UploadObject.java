/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "uploadObject", label = "Upload object")
public record UploadObject(
    @TemplateProperty(
            label = "GCP project",
            id = "uploadOperationProject",
            group = "operation",
            tooltip = "The project where the bucket is located.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.project"))
        @NotBlank
        String project,
    @TemplateProperty(
            label = "Object Storage bucket",
            id = "uploadOperationBucket",
            group = "operation",
            tooltip = "A bucket acts as a directory that organizes a set of objects.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.bucket"))
        @NotBlank
        String bucket,
    @TemplateProperty(
            label = "Document",
            group = "operation",
            id = "uploadOperationDocument",
            tooltip = "Document to be uploaded to Google Cloud Storage.",
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
        String fileName)
    implements ObjectStorageOperation {}
