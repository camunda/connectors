/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.microsoft.common.auth.MicrosoftAuthentication;
import io.camunda.connector.microsoft.common.auth.MicrosoftEntraConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record MsInboundEmailProperties(
    @FEEL
        @TemplateProperty(
            id = "authenticationConfiguration",
            label = "Microsoft Entra ID credential",
            group = "authentication",
            type = PropertyType.Configuration,
            optional = true,
            feel = FeelMode.disabled,
            binding = @TemplateProperty.PropertyBinding(name = "authenticationConfiguration"),
            tooltip =
                "Choose a reusable Microsoft Entra ID credential, or configure one-time"
                    + " authentication parameters below.")
        @Valid
        MicrosoftEntraConfiguration authenticationConfiguration,
    @NestedProperties(
            group = "authentication",
            condition =
                @PropertyCondition(
                    property = "authenticationConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        @Valid
        MicrosoftAuthentication authentication,
    @NestedProperties(group = "pollingConfig") @Valid EmailPollingConfig pollingConfig,
    @NestedProperties(group = "postprocessing") @Valid @NotNull
        EmailProcessingOperation operation) {

  public MsInboundEmailProperties(
      MicrosoftAuthentication authentication,
      EmailPollingConfig pollingConfig,
      EmailProcessingOperation operation) {
    this(null, authentication, pollingConfig, operation);
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
