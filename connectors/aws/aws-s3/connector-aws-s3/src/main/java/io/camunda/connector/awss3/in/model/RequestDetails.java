package io.camunda.connector.awss3.in.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RequestDetails {

  @NotEmpty
  private String bucketName;

  @NotEmpty
  private String objectKey;

  private String filePath;

  @NotEmpty
  private String region;

  @NotNull
  private OperationType operationType;

  private String contentType;
  
}
