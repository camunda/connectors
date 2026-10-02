/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

public abstract class GoogleBaseRequest {

  @TemplateProperty(
      id = "googleCredential",
      label = "Google credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "googleCredential"),
      description =
          "Choose a reusable Google credential, or configure one-time authentication details below.")
  @FEEL
  @Valid
  protected GoogleCredentialConfiguration googleCredential;

  @NestedProperties(
      condition =
          @TemplateProperty.PropertyCondition(
              property = "googleCredential",
              isEmpty = NullableBoolean.TRUE))
  protected Authentication authentication;

  public Authentication getAuthentication() {
    return googleCredential != null ? googleCredential.authentication() : authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  public GoogleCredentialConfiguration getGoogleCredential() {
    return googleCredential;
  }

  public void setGoogleCredential(GoogleCredentialConfiguration googleCredential) {
    this.googleCredential = googleCredential;
  }

  @AssertTrue(message = "No authentication provided by the credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return getAuthentication() != null;
  }

  @Valid
  @JsonIgnore
  public Authentication getInlineAuthenticationWhenNoCredentialBound() {
    return googleCredential != null ? null : authentication;
  }
}
