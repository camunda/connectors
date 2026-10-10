/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public class ObjectStorageRequest {
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "operationDiscriminator")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = UploadObject.class, name = "uploadObject"),
        @JsonSubTypes.Type(value = DownloadObject.class, name = "downloadObject"),
      })
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private ObjectStorageOperation operation;

  @TemplateProperty(
      id = "googleGcsCredential",
      label = "Google Cloud Storage credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "googleGcsCredential"),
      description =
          "Choose a reusable Google Cloud Storage credential, or configure a one-time service"
              + " account key below.")
  @FEEL
  @Valid
  private GcsCredentialConfiguration googleGcsCredential;

  @NestedProperties(
      condition =
          @TemplateProperty.PropertyCondition(
              property = "googleGcsCredential",
              isEmpty = NullableBoolean.TRUE))
  private Authentication authentication;

  public ObjectStorageRequest() {}

  public ObjectStorageOperation getOperation() {
    return operation;
  }

  public void setOperation(ObjectStorageOperation operation) {
    this.operation = operation;
  }

  public Authentication getAuthentication() {
    return googleGcsCredential != null ? googleGcsCredential.authentication() : authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  public GcsCredentialConfiguration getGoogleGcsCredential() {
    return googleGcsCredential;
  }

  public void setGoogleGcsCredential(GcsCredentialConfiguration googleGcsCredential) {
    this.googleGcsCredential = googleGcsCredential;
  }

  @AssertTrue(message = "No authentication provided by the credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return getAuthentication() != null;
  }

  @Valid
  @JsonIgnore
  public Authentication getInlineAuthenticationWhenNoCredentialBound() {
    return googleGcsCredential != null ? null : authentication;
  }
}
