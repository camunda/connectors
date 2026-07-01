/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class S3Request extends AwsBaseRequest {

  @TemplateProperty(
      id = "awsCredential",
      label = "AWS credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "awsCredential"),
      description =
          "Choose a reusable AWS credential. When set, it is bound as a whole to the connector's"
              + " 'awsCredential' input.")
  private AwsCredentialConfiguration awsCredential;

  public AwsCredentialConfiguration getAwsCredential() {
    return awsCredential;
  }

  public void setAwsCredential(AwsCredentialConfiguration awsCredential) {
    this.awsCredential = awsCredential;
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "actionDiscriminator")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = DeleteObject.class, name = "deleteObject"),
        @JsonSubTypes.Type(value = UploadObject.class, name = "uploadObject"),
        @JsonSubTypes.Type(value = DownloadObject.class, name = "downloadObject"),
      })
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private S3Action action;

  public S3Request() {}

  public S3Action getAction() {
    return action;
  }

  public void setAction(S3Action action) {
    this.action = action;
  }
}
