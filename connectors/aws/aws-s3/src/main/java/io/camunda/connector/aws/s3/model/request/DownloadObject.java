/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.model.request;

import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "downloadObject", label = "Download object")
@DocumentReturnFormat(
    group = "downloadObject",
    tooltip =
        "How the downloaded payload should be returned. Document reference uploads the payload to"
            + " the document store; as text decodes it as a String; as JSON parses it into a"
            + " structure you can access via dot notation.")
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
    @TemplateProperty(ignore = true) @Deprecated boolean asFile)
    implements S3Action {}
