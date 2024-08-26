/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.config;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public final class Pop3Config implements Configuration {

  @TemplateProperty(
      label = "POP3 Host",
      group = "protocol",
      id = "data.pop3Host",
      description = "",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Config.pop3Host"))
  @Valid
  @NotNull
  private String pop3Host;

  @TemplateProperty(
      label = "POP3 Port",
      group = "protocol",
      id = "data.pop3Port",
      description = "",
      defaultValue = "995",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Config.pop3Port"))
  @Valid
  @NotNull
  private Integer pop3Port;

  @TemplateProperty(
      label = "Cryptographic protocol",
      description = "Chose the desired cryptographic protocol",
      group = "protocol",
      feel = Property.FeelMode.required,
      type = TemplateProperty.PropertyType.Dropdown,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      defaultValue = "TLS",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(label = "TLS", value = "TLS"),
        @TemplateProperty.DropdownPropertyChoice(label = "None", value = "NONE"),
        @TemplateProperty.DropdownPropertyChoice(label = "SSL", value = "SSL")
      },
      binding =
          @TemplateProperty.PropertyBinding(name = "data.pop3Config.pop3CryptographicProtocol"))
  @NotNull
  private CryptographicProtocol pop3CryptographicProtocol;

  public @Valid @NotNull String getPop3Host() {
    return pop3Host;
  }

  public void setPop3Host(@Valid @NotNull String pop3Host) {
    this.pop3Host = pop3Host;
  }

  public @Valid @NotNull Integer getPop3Port() {
    return pop3Port;
  }

  public void setPop3Port(@Valid @NotNull Integer pop3Port) {
    this.pop3Port = pop3Port;
  }

  public @NotNull CryptographicProtocol getPop3CryptographicProtocol() {
    return pop3CryptographicProtocol;
  }

  public void setPop3CryptographicProtocol(
      @NotNull CryptographicProtocol pop3CryptographicProtocol) {
    this.pop3CryptographicProtocol = pop3CryptographicProtocol;
  }
}
