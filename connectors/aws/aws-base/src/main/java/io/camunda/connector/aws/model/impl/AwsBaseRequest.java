/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import java.util.Objects;

public class AwsBaseRequest {

  // Not @NotNull on the field: a subclass may supply authentication from a bound credential by
  // overriding getAuthentication(). Requiredness is enforced via getter-based validation below,
  // which respects that override; for subclasses without a credential the behaviour is unchanged.
  @TemplateProperty(group = "authentication", id = "type")
  @Valid
  private AwsAuthentication authentication;

  @TemplateProperty(group = "configuration")
  private AwsBaseConfiguration configuration;

  public AwsAuthentication getAuthentication() {
    return authentication;
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

  public AwsBaseConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final AwsBaseConfiguration configuration) {
    this.configuration = configuration;
  }

  @AssertFalse
  public boolean isDefaultCredentialsChainUsedInSaaS() {
    return System.getenv().containsKey("CAMUNDA_CONNECTOR_RUNTIME_SAAS")
        && authentication instanceof AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
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
        && Objects.equals(configuration, that.configuration);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, configuration);
  }

  @Override
  public String toString() {
    return "AwsBaseRequest{"
        + "authentication="
        + authentication
        + ", configuration="
        + configuration
        + "}";
  }
}
