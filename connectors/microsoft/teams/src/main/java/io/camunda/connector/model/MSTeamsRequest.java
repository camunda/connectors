/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.microsoft.common.auth.MicrosoftAuthentication;
import io.camunda.connector.microsoft.common.auth.MicrosoftEntraConfiguration;
import io.camunda.connector.model.request.data.MSTeamsRequestData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record MSTeamsRequest(
    @TemplateProperty(
            id = "authenticationConfiguration",
            label = "Microsoft Entra ID credential",
            group = "authentication",
            type = PropertyType.Configuration,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "authenticationConfiguration"),
            tooltip =
                "Choose a reusable Microsoft Entra ID credential, or configure one-time"
                    + " authentication parameters below.")
        @Valid
        MicrosoftEntraConfiguration authenticationConfiguration,
    // Not @Valid on the component: Modeler leaves the inline authentication's default
    // discriminator (and its now-hidden, unfilled members) in the job input even after a
    // credential is bound, and cascading validation straight into that leftover object would fail
    // its @NotBlank members even though it lost to the credential. Requiredness and shape
    // validation are applied to the effective value instead - see isAuthenticationPresent() and
    // getInlineAuthenticationWhenNoCredentialBound() below.
    @NestedProperties(
            condition =
                @PropertyCondition(
                    property = "authenticationConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        MicrosoftAuthentication authentication,
    @Valid @NotNull MSTeamsRequestData data) {

  public MSTeamsRequest(MicrosoftAuthentication authentication, MSTeamsRequestData data) {
    this(null, authentication, data);
  }

  @Override
  public MicrosoftAuthentication authentication() {
    return authenticationConfiguration != null
        ? authenticationConfiguration.authentication()
        : authentication;
  }

  @AssertTrue(
      message = "No authentication provided by the reusable credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return authentication() != null;
  }

  /**
   * Validates the inline {@link #authentication} only when no credential is bound; see the
   * component's javadoc for why the record component itself isn't {@code @Valid}.
   */
  @Valid
  @JsonIgnore
  public MicrosoftAuthentication getInlineAuthenticationWhenNoCredentialBound() {
    return authenticationConfiguration != null ? null : authentication;
  }
}
