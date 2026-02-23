/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.model.request;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "downloadObject", label = "Download object")
public record DownloadObject(
    @TemplateProperty(
            label = "AWS bucket",
            id = "downloadActionBucket",
            group = "downloadObject",
            tooltip = "Bucket from where an object should be downloaded",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.bucket"))
        @NotBlank
        String bucket,
    @TemplateProperty(
            label = "AWS key",
            id = "downloadActionKey",
            group = "downloadObject",
            tooltip = "Key of the object which should be download",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.key"))
        @NotBlank
        String key,
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
    implements S3Action {}
