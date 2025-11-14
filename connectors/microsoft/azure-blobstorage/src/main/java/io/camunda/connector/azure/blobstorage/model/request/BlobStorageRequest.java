/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.azure.blobstorage.model.request.auth.Authentication;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class BlobStorageRequest {
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "operationDiscriminator")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = UploadBlob.class, name = "uploadBlob"),
        @JsonSubTypes.Type(value = DownloadBlob.class, name = "downloadBlob"),
      })
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private BlobStorageOperation operation;

  private Authentication authentication;

  public BlobStorageRequest() {}

  public BlobStorageOperation getOperation() {
    return operation;
  }

  public void setOperation(BlobStorageOperation operation) {
    this.operation = operation;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }
}
