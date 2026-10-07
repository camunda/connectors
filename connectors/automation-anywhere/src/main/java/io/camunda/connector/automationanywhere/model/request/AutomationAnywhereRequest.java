/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.automationanywhere.model.request.auth.Authentication;
import io.camunda.connector.automationanywhere.model.request.operation.OperationData;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * @param authenticationConfiguration reusable Control Room credential; when bound, its URL and
 *     authentication take precedence over the inline ones (see {@link #authentication()} and {@link
 *     #configuration()}). Declared first so it renders before the fields it gates, as required by
 *     ConditionPropertyOrderRule.
 * @param authentication hidden and validated only while no credential is bound.
 * @param configuration may be absent once a credential is bound: Modeler drops the hidden URL and,
 *     when cleared, the optional timeout. Requiredness is asserted on the effective URL in {@link
 *     #isControlRoomUrlPresent()}.
 */
public record AutomationAnywhereRequest(
    @TemplateProperty(
            id = "authenticationConfiguration",
            label = "Control Room credential",
            group = "configuration",
            type = PropertyType.Configuration,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "authenticationConfiguration"),
            description =
                "Choose a reusable Automation Anywhere credential, or configure one-time Control"
                    + " Room URL and authentication parameters below.")
        @Valid
        AutomationAnywhereConfiguration authenticationConfiguration,
    @FEEL
        @TemplateProperty(group = "authentication", id = "authenticationType")
        @NestedProperties(
            condition =
                @PropertyCondition(
                    property = "authenticationConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        Authentication authentication,
    @Valid @FEEL @TemplateProperty(group = "configuration", id = "configuration")
        Configuration configuration,
    @Valid @FEEL @NotNull @TemplateProperty(group = "operation", id = "operationType")
        OperationData operation) {

  /** Convenience constructor for the shape without a bound credential. */
  public AutomationAnywhereRequest(
      Authentication authentication, Configuration configuration, OperationData operation) {
    this(null, authentication, configuration, operation);
  }

  /** The bound credential's authentication wins over the inline one. */
  @Override
  public Authentication authentication() {
    return authenticationConfiguration != null
        ? authenticationConfiguration.authentication()
        : authentication;
  }

  /** The bound credential's Control Room URL wins over the inline one; the timeout stays inline. */
  @Override
  public Configuration configuration() {
    if (authenticationConfiguration == null) {
      return configuration;
    }
    return new Configuration(
        authenticationConfiguration.controlRoomUrl(),
        configuration != null ? configuration.connectionTimeoutInSeconds() : null);
  }

  @AssertTrue(message = "No authentication provided by the credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return authentication() != null;
  }

  @AssertTrue(message = "No Control Room URL provided by the credential or the element template")
  @JsonIgnore
  public boolean isControlRoomUrlPresent() {
    Configuration effective = configuration();
    return effective != null && effective.controlRoomUrl() != null;
  }

  /**
   * Validates the inline authentication only when no credential is bound, so a leftover inline
   * discriminator default does not fail validation once the credential supplies the login.
   */
  @Valid
  @JsonIgnore
  public Authentication getInlineAuthenticationWhenNoCredentialBound() {
    return authenticationConfiguration != null ? null : authentication;
  }
}
