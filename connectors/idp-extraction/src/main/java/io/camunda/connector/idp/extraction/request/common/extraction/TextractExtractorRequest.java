/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.extraction;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "textract", label = "AWS Textract extractor")
public record TextractExtractorRequest(
    @TemplateProperty(
            id = "awsAuthType",
            label = "Authentication type",
            group = "extractor",
            type = Dropdown,
            defaultValue = "credentials",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Credentials",
                  value = "credentials"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Default Credentials Chain (Hybrid/Self-Managed only)",
                  value = "defaultCredentialsChain")
            })
        String awsAuthType,
    @TemplateProperty(
            id = "accessKey",
            label = "Access key",
            tooltip = "IAM access key of a user with the necessary permissions",
            group = "extractor",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "extractor.awsAuthType",
                    equals = "credentials"))
        String accessKey,
    @TemplateProperty(
            id = "secretKey",
            label = "Secret key",
            tooltip = "AWS secret access key of a user with permissions for Amazon Textract and S3",
            group = "extractor",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "extractor.awsAuthType",
                    equals = "credentials"))
        String secretKey,
    @TemplateProperty(
            id = "region",
            group = "extractor",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String region,
    @TemplateProperty(
            id = "bucketName",
            label = "AWS S3 Bucket name",
            group = "extractor",
            type = TemplateProperty.PropertyType.Text,
            tooltip = "Where the document will be stored temporarily during Textract analysis",
            defaultValue = "idp-extraction-connector",
            binding = @TemplateProperty.PropertyBinding(name = "bucketName"),
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String bucketName)
    implements ExtractionProvider {}
