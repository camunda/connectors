/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public final class AwsProvider extends AwsBaseRequest implements ProviderConfig {

  @TemplateProperty(
      id = "s3BucketName",
      label = "AWS S3 Bucket name",
      group = "bucket",
      type = TemplateProperty.PropertyType.Text,
      description =
          "Specify the name of the AWS S3 bucket where document will be stored temporarily during Textract analysis",
      defaultValue = "idp-extraction-connector",
      binding = @TemplateProperty.PropertyBinding(name = "s3BucketName"),
      feel = Property.FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  String s3BucketName;

  public String getS3BucketName() {
    return s3BucketName;
  }
}
