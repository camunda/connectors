/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class EmailProperties {

  @TemplateProperty(group = "authentication", id = "type")
  @Valid
  @NotNull
  private Authentication authentication;

  @Valid
  @NestedProperties(addNestedPath = false)
  private EmailListenerData data;

  public @Valid @NotNull Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(@Valid @NotNull Authentication authentication) {
    this.authentication = authentication;
  }

  public EmailListenerData getData() {
    return data;
  }

  public void setData(EmailListenerData data) {
    this.data = data;
  }
}
