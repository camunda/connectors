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

@TemplateSubType(id = "deleteObject", label = "Delete object")
public record DeleteObject(
    @TemplateProperty(
            label = "AWS bucket",
            id = "deleteActionBucket",
            group = "deleteObject",
            tooltip = "Bucket from where an object should be deleted",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.bucket"))
        @NotBlank
        String bucket,
    @TemplateProperty(
            label = "AWS key",
            id = "deleteActionKey",
            group = "deleteObject",
            tooltip = "Key of the object which should be deleted",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.key"))
        @NotBlank
        String key)
    implements S3Action {}
