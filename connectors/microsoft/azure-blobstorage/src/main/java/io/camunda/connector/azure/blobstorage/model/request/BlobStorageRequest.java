/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.azure.blobstorage.model.request.auth.Authentication;
import io.camunda.connector.azure.blobstorage.model.request.auth.AzureBlobStorageConfiguration;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

  @TemplateProperty(
      id = "authenticationConfiguration",
      label = "Azure Blob Storage credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "authenticationConfiguration"),
      tooltip =
          "Choose a reusable Azure Blob Storage credential, or configure one-time authentication"
              + " parameters below.")
  @Valid
  private AzureBlobStorageConfiguration authenticationConfiguration;

  // Not @Valid on the field: Modeler leaves the inline authentication's default discriminator
  // (and its now-hidden, unfilled members) in the job input even after a credential is bound, and
  // cascading validation straight into that leftover object would fail its @NotBlank members even
  // though it lost to the credential. Requiredness and shape validation are applied to the
  // effective value instead - see isAuthenticationPresent() and
  // getInlineAuthenticationWhenNoCredentialBound() below.
  @NestedProperties(
      condition =
          @PropertyCondition(
              property = "authenticationConfiguration",
              isEmpty = NullableBoolean.TRUE))
  private Authentication authentication;

  public BlobStorageRequest() {}

  public BlobStorageOperation getOperation() {
    return operation;
  }

  public void setOperation(BlobStorageOperation operation) {
    this.operation = operation;
  }

  public AzureBlobStorageConfiguration getAuthenticationConfiguration() {
    return authenticationConfiguration;
  }

  public void setAuthenticationConfiguration(
      AzureBlobStorageConfiguration authenticationConfiguration) {
    this.authenticationConfiguration = authenticationConfiguration;
  }

  public Authentication getAuthentication() {
    return authenticationConfiguration != null
        ? authenticationConfiguration.authentication()
        : authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  @AssertTrue(
      message = "No authentication provided by the reusable credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return getAuthentication() != null;
  }

  /**
   * Validates the inline {@link #authentication} only when no credential is bound; see the field's
   * javadoc for why the field itself isn't {@code @Valid}.
   */
  @Valid
  @JsonIgnore
  public Authentication getInlineAuthenticationWhenNoCredentialBound() {
    return authenticationConfiguration != null ? null : authentication;
  }
}
