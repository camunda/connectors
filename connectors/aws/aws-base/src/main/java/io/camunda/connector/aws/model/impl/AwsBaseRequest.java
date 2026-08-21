/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import java.util.Objects;

public class AwsBaseRequest {

  // Declared first so it renders (and is emitted in properties[]) before the fallback fields it
  // gates below - required both for UX (pick a credential before falling back to inline auth) and
  // by ConditionPropertyOrderRule (a condition's referenced property must appear earlier).
  @TemplateProperty(
      id = "awsCredential",
      label = "AWS credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "awsCredential"),
      description =
          "Choose a reusable AWS credential, or configure one-time authentication parameters"
              + " below.")
  @Valid
  private AwsCredentialConfiguration awsCredential;

  // Not @NotNull on the field: a subclass may supply authentication from a bound credential by
  // overriding getAuthentication(). Requiredness is enforced via getter-based validation below,
  // which respects that override; for subclasses without a credential the behaviour is unchanged.
  // Hidden and un-required (via the isEmpty condition) once a credential is chosen above.
  @NestedProperties(
      condition =
          @TemplateProperty.PropertyCondition(
              property = "awsCredential",
              isEmpty = NullableBoolean.TRUE))
  @Valid
  private AwsAuthentication authentication;

  // Hidden and un-required (via the isEmpty condition) once a credential is chosen above: the
  // bound credential's own region always wins (see getConfiguration()), so leaving `region`
  // visible and required here would force a value that's silently discarded.
  @NestedProperties(
      condition =
          @TemplateProperty.PropertyCondition(
              property = "awsCredential",
              isEmpty = NullableBoolean.TRUE))
  @TemplateProperty(group = "configuration")
  private AwsBaseConfiguration configuration;

  public AwsCredentialConfiguration getAwsCredential() {
    return awsCredential;
  }

  public void setAwsCredential(AwsCredentialConfiguration awsCredential) {
    this.awsCredential = awsCredential;
  }

  /**
   * Per-connector consumption of the bound AWS credential: when a credential (configuration) is
   * bound, its authentication takes precedence over the inline authentication; inline is the
   * fallback.
   */
  public AwsAuthentication getAuthentication() {
    return awsCredential != null ? awsCredential.authentication() : authentication;
  }

  public void setAuthentication(final AwsAuthentication authentication) {
    this.authentication = authentication;
  }

  /**
   * Authentication is required, but may come from a bound credential in subclasses that override
   * {@link #getAuthentication()}. Validating the getter (not the field) respects that override
   * while preserving the original requirement for subclasses without a credential.
   */
  @AssertTrue(message = "Authentication is required")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return getAuthentication() != null;
  }

  /**
   * When a credential is bound, its region drives the configuration; the inline endpoint (if any)
   * is preserved.
   */
  public AwsBaseConfiguration getConfiguration() {
    if (awsCredential == null) {
      return configuration;
    }
    String endpoint = configuration != null ? configuration.endpoint() : null;
    return new AwsBaseConfiguration(awsCredential.region(), endpoint);
  }

  public void setConfiguration(final AwsBaseConfiguration configuration) {
    this.configuration = configuration;
  }

  @AssertFalse
  public boolean isDefaultCredentialsChainUsedInSaaS() {
    // Evaluate the effective authentication (getAuthentication()) rather than the raw field, so the
    // SaaS restriction also applies when the default-credentials-chain auth comes from a bound
    // credential instead of the inline authentication field.
    return System.getenv().containsKey("CAMUNDA_CONNECTOR_RUNTIME_SAAS")
        && getAuthentication()
            instanceof AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AwsBaseRequest that = (AwsBaseRequest) o;
    return Objects.equals(authentication, that.authentication)
        && Objects.equals(configuration, that.configuration)
        && Objects.equals(awsCredential, that.awsCredential);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, configuration, awsCredential);
  }

  @Override
  public String toString() {
    return "AwsBaseRequest{"
        + "authentication="
        + authentication
        + ", configuration="
        + configuration
        + ", awsCredential="
        + awsCredential
        + "}";
  }
}
