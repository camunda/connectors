/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.model.request;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "uploadObject", label = "Upload object")
public record UploadObject(
    @TemplateProperty(
            label = "AWS bucket",
            id = "uploadActionBucket",
            group = "uploadObject",
            tooltip = "Bucket from where an object should be uploaded",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.bucket"))
        @NotBlank
        String bucket,
    @TemplateProperty(
            label = "AWS key",
            id = "uploadActionKey",
            group = "uploadObject",
            tooltip =
                "Key of the uploaded object, if not given. The file name from the document metadata will be used",
            optional = true,
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "action.key"))
        String key,
    @TemplateProperty(
            label = "Document",
            group = "uploadObject",
            id = "uploadActionDocument",
            tooltip = "Document to be uploaded on AWS S3",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.required,
            binding = @TemplateProperty.PropertyBinding(name = "action.document"))
        @NotNull
        Document document)
    implements S3Action {}
