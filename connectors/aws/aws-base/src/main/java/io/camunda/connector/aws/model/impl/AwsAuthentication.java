/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

// Note on `defaultImpl`. This is left for backwards compatibility
// with existing BPMN diagrams.
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    defaultImpl = AwsStaticCredentialsAuthentication.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsStaticCredentialsAuthentication.class, name = "credentials"),
  @JsonSubTypes.Type(
      value = AwsDefaultCredentialsChainAuthentication.class,
      name = "defaultCredentialsChain"),
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "authentication",
    name = "type",
    defaultValue = "credentials",
    description =
        "Specify AWS authentication strategy. Learn more at the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-lambda/#aws-authentication-types\" target=\"_blank\">documentation page</a>")
public sealed interface AwsAuthentication
    permits AwsDefaultCredentialsChainAuthentication, AwsStaticCredentialsAuthentication {
  @TemplateSubType(id = "credentials", label = "Credentials")
  record AwsStaticCredentialsAuthentication(
      @TemplateProperty(
              group = "authentication",
              label = "Access key",
              description =
                  "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
          @NotBlank
          String accessKey,
      @TemplateProperty(
              group = "authentication",
              label = "Secret key",
              description =
                  "Provide a secret key of a user with permissions to invoke specified AWS Lambda function")
          @NotBlank
          String secretKey)
      implements AwsAuthentication {}

  @TemplateSubType(
      id = "defaultCredentialsChain",
      label = "Default Credentials Chain (Hybrid/Self-Managed only)")
  record AwsDefaultCredentialsChainAuthentication() implements AwsAuthentication {}
}
