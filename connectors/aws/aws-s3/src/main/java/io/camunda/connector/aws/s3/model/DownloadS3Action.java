/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "downloadObject", label = "Download document")
public record DownloadS3Action(
    @TemplateProperty(
            label = "AWS bucket",
            id = "downloadActionBucket",
            group = "downloadObject",
            tooltip = "Bucket from where an object should be downloaded",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.bucket"))
        @Valid
        @NotNull
        String bucket,
    @TemplateProperty(
            label = "AWS key",
            id = "downloadActionKey",
            group = "downloadObject",
            tooltip = "Key of the object which should be download",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.key"))
        @Valid
        @NotNull
        String key,
    @TemplateProperty(
            label = "Create document",
            id = "downloadActionAsFile",
            group = "downloadObject",
            tooltip = "....",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true",
            binding = @TemplateProperty.PropertyBinding(name = "action.asFile"))
        @Valid
        @NotNull
        boolean asFile)
    implements S3Action {}
