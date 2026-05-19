/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;

public record TextractRequestData(
    @TemplateProperty(
            group = "document",
            label = "Document source",
            description = "Document source of the input document that should be analyzed.",
            feel = FeelMode.disabled,
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "S3")
        DocumentLocationType documentLocationType,
    @TemplateProperty(
            group = "document",
            label = "Document bucket",
            description = "S3 bucket that contains document that should be analyzed.",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.documentLocationType",
                    equals = "S3"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String documentS3Bucket,
    @TemplateProperty(
            group = "document",
            label = "Document name",
            description = "S3 document name of the document that should be analyzed.",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.documentLocationType",
                    equals = "S3"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String documentName,
    @TemplateProperty(
            group = "document",
            label = "Document version",
            description = "S3 document version of the document that should be analyzed.",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.documentLocationType",
                    equals = "S3"))
        String documentVersion,
    @TemplateProperty(
            group = "document",
            label = "Camunda Document",
            description = "The Camunda document of the process that should be analyzed.",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.String,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.documentLocationType",
                    equals = "UPLOADED"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        Document document,
    @TemplateProperty(
            label = "Execution type",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "POLLING",
            feel = FeelMode.disabled,
            description =
                "How the document should be processes. See more info in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-textract/#execution-types\" target=\"_blank\">documentation</a>.",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.documentLocationType",
                    equals = "S3"))
        @NotNull
        TextractExecutionType executionType,
    @TemplateProperty(
            label = "Analyze tables",
            description =
                "Select this to return information about the tables that are detected in the input document.",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean analyzeTables,
    @TemplateProperty(
            label = "Analyze form",
            description = "Select this to return information detected form data.",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean analyzeForms,
    @TemplateProperty(
            label = "Analyze signatures",
            description = "Select this to return the locations of detected signatures.",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean analyzeSignatures,
    @TemplateProperty(
            label = "Analyze layout",
            description = "Select this to return information about the layout of the document.",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "false")
        @NotNull
        boolean analyzeLayout,
    @TemplateProperty(
            label = "Analyze queries",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean analyzeQueries,
    @TemplateProperty(
            label = "Query",
            group = "input",
            type = TemplateProperty.PropertyType.String,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.analyzeQueries",
                    equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
        String query,
    @TemplateProperty(
            group = "advanced",
            label = "Client request token",
            description = "The idempotent token that you use to identify the start request.",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String clientRequestToken,
    @TemplateProperty(
            group = "advanced",
            label = "Job tag",
            description =
                "An identifier that you specify that's included in the completion notification published to the Amazon SNS topic.",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String jobTag,
    @TemplateProperty(
            group = "advanced",
            label = "KMS key ID",
            description = "The KMS key used to encrypt the inference results.",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String kmsKeyId,
    @TemplateProperty(
            group = "advanced",
            label = "Notification channel role ARN",
            description =
                "The Amazon SNS topic role ARN that you want Amazon Textract to publish the completion status of the operation to.",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String notificationChannelRoleArn,
    @TemplateProperty(
            group = "advanced",
            label = "Notification channel SNS topic ARN",
            description =
                "The Amazon SNS topic ARN that you want Amazon Textract to publish the completion status of the operation to.",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"))
        String notificationChannelSnsTopicArn,
    @TemplateProperty(
            group = "input",
            label = "Output S3 bucket",
            description = "The name of the bucket your output will go to.",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String outputConfigS3Bucket,
    @TemplateProperty(
            group = "input",
            label = "Output S3 prefix",
            description = "The prefix of the object key that the output will be saved to.",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.executionType",
                    equals = "ASYNC"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String outputConfigS3Prefix) {

  @TemplateProperty(ignore = true)
  public static final String WRONG_NOTIFICATION_VALUES_MSG =
      "Either both notification values role ARN and topic ARN must be filled in or none of them.";

  @AssertTrue(message = WRONG_NOTIFICATION_VALUES_MSG)
  public boolean isValidNotificationProperties() {
    if (executionType != TextractExecutionType.ASYNC) {
      return true;
    }
    return StringUtils.isNoneBlank(notificationChannelRoleArn, notificationChannelSnsTopicArn)
        || StringUtils.isAllBlank(notificationChannelRoleArn, notificationChannelSnsTopicArn);
  }
}
