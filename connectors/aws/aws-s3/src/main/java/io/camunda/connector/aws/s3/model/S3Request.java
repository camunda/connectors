package io.camunda.connector.aws.s3.model;

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
      property = "action")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = DeleteS3Action.class, name = "deleteObject"),
        @JsonSubTypes.Type(value = UploadS3Action.class, name = "uploadObject"),
        @JsonSubTypes.Type(value = DownloadS3Action.class, name = "downloadObject"),
      })
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private S3Action data;
}
