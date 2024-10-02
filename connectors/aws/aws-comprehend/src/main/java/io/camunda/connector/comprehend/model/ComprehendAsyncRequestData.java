/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
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
            feel = Property.FeelMode.disabled,
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
                "Determines the text extraction actions for PDF files. More text extraction options "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/dg/idp-set-textract-options.html\"> info</a>")
        @NotNull
        ComprehendDocumentReadMode documentReadMode,
    @TemplateProperty(
            id = "async.documentReadAction",
            label = "Document read action",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "TEXTRACT_DETECT_DOCUMENT_TEXT",
            feel = Property.FeelMode.disabled,
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
            feel = Property.FeelMode.disabled,
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "false",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.async.documentReadAction",
                    equals = "TEXTRACT_ANALYZE_DOCUMENT"))
        @NotNull
        boolean featureTypeTables,
    @TemplateProperty(
            id = "async.featureTypeForms",
            label = "Analyze forms",
            group = "input",
            feel = Property.FeelMode.disabled,
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "false",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.async.documentReadAction",
                    equals = "TEXTRACT_ANALYZE_DOCUMENT"))
        @NotNull
        boolean featureTypeForms,
    @TemplateProperty(
            label = "Input S3 URI",
            group = "input",
            description =
                "The Amazon S3 URI for the input data. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_InputDataConfig.html#comprehend-Type-InputDataConfig-S3Uri\">More info.</a>")
        @NotNull
        String inputS3Uri,
    @TemplateProperty(
            label = "Input Format",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            feel = Property.FeelMode.disabled,
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
                "Specifies how the text in an input file should be processed. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_InputDataConfig.html#comprehend-Type-InputDataConfig-InputFormat\">More info.</a>")
        ComprehendInputFormat comprehendInputFormat,
    @TemplateProperty(
            group = "input",
            label = "Client request token",
            description =
                "A unique identifier for the request. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-ClientRequestToken\">More info.</a>",
            optional = true)
        String clientRequestToken,
    @TemplateProperty(
            group = "input",
            label = "Data Access Role ARN",
            description =
                "The ARN of the IAM role that grants Amazon Comprehend read access to your input data. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-DataAccessRoleArn\">More info.</a>")
        @NotNull
        String dataAccessRoleArn,
    @TemplateProperty(
            group = "input",
            label = "Document Classifier ARN",
            description =
                "The ARN of the document classifier to use to process the job. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-DocumentClassifierArn\">More info.</a>\n",
            optional = true)
        String documentClassifierArn,
    @TemplateProperty(
            group = "input",
            label = "Flywheel ARN",
            description =
                "The ARN of the flywheel associated with the model. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-FlywheelArn\">More info.</a>\n",
            optional = true)
        String flywheelArn,
    @TemplateProperty(
            group = "input",
            label = "Job name",
            description =
                "The identifier of the job. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-JobName\">More info.</a>\n",
            optional = true)
        String jobName,
    @TemplateProperty(
            group = "input",
            label = "Output S3 URI",
            description =
                "S3 location where the date will be written. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_OutputDataConfig.html#comprehend-Type-OutputDataConfig-S3Uri\">More info.</a>\n")
        @NotNull
        String outputS3Uri,
    @TemplateProperty(
            group = "input",
            label = "Output Kms Key Id",
            description =
                "KMS key id used for encrypt the output result. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_OutputDataConfig.html#comprehend-Type-OutputDataConfig-KmsKeyId\">More info.</a>\n",
            optional = true)
        String outputKmsKeyId,
    @FEEL
        @TemplateProperty(
            group = "input",
            label = "Tags",
            description =
                "Tags to associate with the document classification job. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-Tags\">More info.</a>\n",
            optional = true,
            feel = Property.FeelMode.required)
        Map<String, String> tags,
    @TemplateProperty(
            group = "input",
            label = "Volume Kms Key Id",
            description =
                "KMS that Amazon Comprehend uses to encrypt data on the storage volume "
                    + "attached to the ML compute instance. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_StartDocumentClassificationJob.html#comprehend-StartDocumentClassificationJob-request-VolumeKmsKeyId\">More info.</a>\n",
            optional = true)
        String volumeKmsKeyId,
    @FEEL
        @TemplateProperty(
            group = "input",
            label = "Security group ids",
            description =
                "The ID number for a security group on an instance of your private VPC. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_VpcConfig.html#comprehend-Type-VpcConfig-SecurityGroupIds\">More info.</a>",
            optional = true,
            feel = Property.FeelMode.required)
        List<String> securityGroupIds,
    @FEEL
        @TemplateProperty(
            group = "input",
            label = "Subnets",
            description =
                "The ID for each subnet being used in your private VPC. "
                    + "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_VpcConfig.html#comprehend-Type-VpcConfig-Subnets\">More info.</a>",
            optional = true,
            feel = Property.FeelMode.required)
        List<String> subnets)
    implements ComprehendRequestData {
  @Override
  public ComprehendDocumentReadAction getDocumentReadAction() {
    return documentReadAction;
  }

  @Override
  public ComprehendDocumentReadMode getDocumentReadMode() {
    return documentReadMode;
  }

  @Override
  public boolean getFeatureTypeTables() {
    return featureTypeTables;
  }

  @Override
  public boolean getFeatureTypeForms() {
    return featureTypeForms;
  }
}
