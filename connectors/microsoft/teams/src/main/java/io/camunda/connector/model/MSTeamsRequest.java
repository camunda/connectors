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
            description =
                "Choose a reusable Microsoft Entra ID credential, or configure one-time"
                    + " authentication parameters below.")
        @Valid
        MicrosoftEntraConfiguration authenticationConfiguration,
    @NestedProperties(
            condition =
                @PropertyCondition(
                    property = "authenticationConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        @Valid
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
}
