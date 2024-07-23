/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model;

import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TextractRequestData(
    @TemplateProperty(
            label = "Execution type",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "ASYNC",
            feel = FeelMode.disabled,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "ASYNC", label = "Asynchronous"),
              @TemplateProperty.DropdownPropertyChoice(value = "SYNC", label = "Real-time"),
              @TemplateProperty.DropdownPropertyChoice(value = "POLLING", label = "Polling")
            },
            description = "Endpoint inference type")
        @NotNull
        TextractExecutionType executionType,
    @TemplateProperty(
            group = "input",
            label = "Document bucket",
            description = "S3 bucket that contains document that needs to be processed")
        @NotBlank
        String documentS3Bucket,
    @TemplateProperty(
            group = "input",
            label = "Document path",
            description = "S3 document path to be processed")
        @NotBlank
        String documentName,
    @TemplateProperty(
            group = "input",
            label = "Document version",
            description = "S3 document version to be processed",
            optional = true)
        String documentVersion,
    @TemplateProperty(
            label = "Analyze tables",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean analyzeTables,
    @TemplateProperty(
            label = "Analyze form",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean analyzeForms,
    @TemplateProperty(
            label = "Analyze signatures",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean analyzeSignatures,
    @TemplateProperty(
            label = "Analyze layout",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean analyzeLayout,
    @TemplateProperty(
            group = "input",
            label = "Client request token",
            description = "The idempotent token that you use to identify the start request",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String clientRequestToken,
    @TemplateProperty(
            group = "input",
            label = "Job tag",
            description =
                "An identifier that you specify that's included in the completion notification published to the Amazon SNS topic",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String jobTag,
    @TemplateProperty(
            group = "input",
            label = "KMS key ID",
            description = "The KMS key used to encrypt the inference results",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String kmsKeyId,
    @TemplateProperty(
            group = "input",
            label = "Notification channel role ARN",
            description =
                "The Amazon SNS topic role ARN that you want Amazon Textract to publish the completion status of the operation to",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String notificationChannelRoleArn,
    @TemplateProperty(
            group = "input",
            label = "Notification channel SNS topic ARN",
            description =
                "The Amazon SNS topic ARN that you want Amazon Textract to publish the completion status of the operation to",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String notificationChannelSnsTopicArn,
    @TemplateProperty(
            group = "input",
            label = "Output S3 bucket",
            description = "The name of the bucket your output will go to",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String outputConfigS3Bucket,
    @TemplateProperty(
            group = "input",
            label = "Output S3 prefix",
            description = "The prefix of the object key that the output will be saved to",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String outputConfigS3Prefix) {}
