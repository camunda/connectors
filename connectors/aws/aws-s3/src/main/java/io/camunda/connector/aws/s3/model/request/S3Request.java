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
import io.camunda.connector.generator.java.annotation.NestedProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class S3Request extends AwsBaseRequest {

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "actionDiscriminator")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = DeleteS3Action.class, name = "deleteObject"),
        @JsonSubTypes.Type(value = UploadObject.class, name = "uploadObject"),
        @JsonSubTypes.Type(value = DownloadS3Action.class, name = "downloadObject"),
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
