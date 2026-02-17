/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@TemplateSubType(id = "async", label = "Async")
public record ComprehendAsyncRequestData(
    @TemplateProperty(
            id = "async.documentReadMode",
            label = "Document read mode",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "SERVICE_DEFAULT",
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "SERVICE_DEFAULT",
                  label = "Service default"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "FORCE_DOCUMENT_READ_ACTION",
                  label = "Force document read action"),
              @TemplateProperty.DropdownPropertyChoice(value = "NO_DATA", label = "None"),
            },
            description =
                "Determines <a href=\"https://docs.aws.amazon.com/comprehend/latest/dg/idp-set-textract-options.html\">text extraction actions</a> for PDF files.")
        @NotNull
        ComprehendDocumentReadMode documentReadMode,
    @TemplateProperty(
            id = "async.documentReadAction",
            label = "Document read action",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "TEXTRACT_DETECT_DOCUMENT_TEXT",
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "TEXTRACT_DETECT_DOCUMENT_TEXT",
                  label = "Detect document text"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "TEXTRACT_ANALYZE_DOCUMENT",
                  label = "Analyze document"),
              @TemplateProperty.DropdownPropertyChoice(value = "NO_DATA", label = "None")
            },
            description =
                "Textract API operation that uses to extract text from PDF files and image files.",
            tooltip =
                "<a href=\"https://docs.aws.amazon.com/comprehend/latest/dg/idp-set-textract-options.html\"target=\"_blank\">more info</a>")
        @NotNull
        ComprehendDocumentReadAction documentReadAction,
    @TemplateProperty(
            id = "async.featureTypeTables",
            label = "Analyze tables",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "false",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.async.documentReadAction",
                    equals = "TEXTRACT_ANALYZE_DOCUMENT"))
        boolean featureTypeTables,
    @TemplateProperty(
            id = "async.featureTypeForms",
            label = "Analyze forms",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "false",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.async.documentReadAction",
                    equals = "TEXTRACT_ANALYZE_DOCUMENT"))
        boolean featureTypeForms,
    @TemplateProperty(
            label = "Inputs' S3 URI",
            group = "input",
            description =
                "The <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_InputDataConfig.html#comprehend-Type-InputDataConfig-S3Uri\">S3Uri</a> for input data.")
        @NotNull
        String inputS3Uri,
    @TemplateProperty(
            label = "Input file processing mode",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            feel = FeelMode.disabled,
            defaultValue = "ONE_DOC_PER_FILE",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "ONE_DOC_PER_FILE",
                  label = "Each file is considered a separate document"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "ONE_DOC_PER_LINE",
                  label = "Each line in a file is considered a separate document"),
              @TemplateProperty.DropdownPropertyChoice(value = "NO_DATA", label = "None")
            },
            description =
                "Specifies how to <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_InputDataConfig.html#comprehend-Type-InputDataConfig-InputFormat\">process input data</a>.")
        ComprehendInputFormat comprehendInputFormat,
    @TemplateProperty(
            group = "input",
            label = "Client request token",
            description =
                "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-ClientRequestToken\">Unique identifier</a> for the processing.",
            optional = true)
        String clientRequestToken,
    @TemplateProperty(
            group = "input",
            label = "Data Access Role's ARN",
            description =
                "ARN of IAM role that grants <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-DataAccessRoleArn\">Amazon Comprehend read access</a> to input data.")
        @NotNull
        String dataAccessRoleArn,
    @TemplateProperty(
            group = "input",
            label = "Document Classifier's ARN",
            description =
                "The <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-DocumentClassifierArn\">ARN of document classifier</a> to process input data.")
        @NotNull
        String documentClassifierArn,
    @TemplateProperty(
            group = "input",
            label = "Flywheel's ARN",
            description =
                "ARN of <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-FlywheelArn\">Flywheel</a> for processing model.",
            optional = true)
        String flywheelArn,
    @TemplateProperty(
            group = "input",
            label = "Job name",
            description =
                "The identifier of the job. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-JobName\">More info.</a>",
            optional = true)
        String jobName,
    @TemplateProperty(
            group = "input",
            label = "Output's S3 URI",
            description =
                "The <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_InputDataConfig.html#comprehend-Type-OutputDataConfig-S3Uri\">S3Uri</a> for output data.")
        @NotNull
        String outputS3Uri,
    @TemplateProperty(
            group = "input",
            label = "Outputs KMS Key Id",
            description =
                "KMS' key Id used to <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_OutputDataConfig.html#comprehend-Type-OutputDataConfig-KmsKeyId\">encrypt output data</a>.",
            optional = true)
        String outputKmsKeyId,
    @FEEL
        @TemplateProperty(
            group = "input",
            label = "Tags",
            description =
                "Tags to <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-Tags\">associate progressing with a document classification</a>.",
            optional = true,
            feel = FeelMode.required)
        Map<String, String> tags,
    @TemplateProperty(
            group = "input",
            label = "VolumeKmsKeyId",
            description =
                "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-VolumeKmsKeyId\">KMS to encrypt data on storage</a> attached to compute instance.",
            optional = true)
        String volumeKmsKeyId,
    @FEEL
        @TemplateProperty(
            group = "input",
            label = "Security group Ids",
            description =
                "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_VpcConfig.html#comprehend-Type-VpcConfig-SecurityGroupIds\">ID for security group</a> on instance of private VPC.",
            optional = true,
            feel = FeelMode.required)
        List<String> securityGroupIds,
    @FEEL
        @TemplateProperty(
            group = "input",
            label = "Subnets",
            description =
                "ID for each <a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_VpcConfig.html#comprehend-Type-VpcConfig-Subnets\">subnet used in VPC</a>.",
            optional = true,
            feel = FeelMode.required)
        List<String> subnets)
    implements ComprehendRequestData {}
