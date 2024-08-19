/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.protocols.Imap;
import io.camunda.connector.email.protocols.Pop3;
import io.camunda.connector.email.protocols.Protocol;
import io.camunda.connector.email.protocols.Smtp;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class EmailRequest {
  @TemplateProperty(group = "authentication", id = "authentication")
  @Valid
  @NotNull
  private Authentication authentication;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "protocol")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = Imap.class, name = "imap"),
        @JsonSubTypes.Type(value = Pop3.class, name = "pop3"),
        @JsonSubTypes.Type(value = Smtp.class, name = "smtp"),
      })
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private Protocol protocol;
}
