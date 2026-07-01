/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.idp.extraction.model.providers.aws.TextExtractionEngineType;
import jakarta.validation.constraints.NotNull;

/**
 * @deprecated Legacy IDP extraction provider model, used only by {@link
 *     io.camunda.connector.idp.extraction.ExtractionConnectorFunction}. The structured /
 *     unstructured / classification connectors use the {@code request.common} provider model
 *     instead. Retained for backwards compatibility; no removal currently planned.
 */
@Deprecated(since = "8.9")
@TemplateSubType(id = "aws", label = "Amazon Web Services Provider")
public final class AwsProvider extends AwsBaseRequest implements ProviderConfig {

  @TemplateProperty(
      id = "s3BucketName",
      label = "AWS S3 Bucket name",
      group = "configuration",
      type = TemplateProperty.PropertyType.Text,
      tooltip =
          "Specify the name of the AWS S3 bucket where document will be stored temporarily during Textract analysis",
      defaultValue = "idp-extraction-connector",
      binding = @TemplateProperty.PropertyBinding(name = "s3BucketName"),
      feel = FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  String s3BucketName;

  @TemplateProperty(
      id = "extractionEngineType",
      label = "Extraction engine type",
      group = "configuration",
      type = Dropdown,
      binding = @TemplateProperty.PropertyBinding(name = "extractionEngineType"),
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      feel = FeelMode.disabled,
      defaultValue = "AWS_TEXTRACT",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(label = "Aws Textract", value = "AWS_TEXTRACT"),
        @TemplateProperty.DropdownPropertyChoice(label = "Apache Pdfbox", value = "APACHE_PDFBOX")
      })
  @NotNull
  TextExtractionEngineType extractionEngineType;

  public String getS3BucketName() {
    return s3BucketName;
  }

  public TextExtractionEngineType getExtractionEngineType() {
    return extractionEngineType;
  }
}
