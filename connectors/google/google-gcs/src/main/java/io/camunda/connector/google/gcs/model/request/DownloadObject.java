/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(
    id = "downloadObject",
    label = "Download object",
    description = "Download an object from a Google Cloud Storage bucket",
    keywords = {"download", "get object", "fetch file", "retrieve object", "export from bucket"})
@DocumentReturnFormat(
    group = "operation",
    tooltip =
        "How the downloaded payload should be returned. Document reference uploads the payload to"
            + " the document store; as text decodes it as a String; as JSON parses it into a"
            + " structure you can access via dot notation.")
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
            tooltip = "Name of the object in the bucket to download.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "operation.fileName"))
        @NotBlank
        String fileName,
    @TemplateProperty(ignore = true) @Deprecated boolean asDocument)
    implements ObjectStorageOperation {}
