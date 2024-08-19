/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.protocols.actions.SmtpAction;
import io.camunda.connector.email.protocols.actions.SmtpSendEmail;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public non-sealed class Smtp implements Protocol {
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "protocol")
  @JsonSubTypes(value = {@JsonSubTypes.Type(value = SmtpSendEmail.class, name = "sendEmail")})
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private SmtpAction smtpAction;
}
