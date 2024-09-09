/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.protocols.actions.Action;
import io.camunda.connector.email.protocols.actions.Pop3Action;
import io.camunda.connector.email.protocols.actions.Pop3ListEmails;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public final class Pop3 implements Protocol {
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "action")
  @JsonSubTypes(value = {@JsonSubTypes.Type(value = Pop3ListEmails.class, name = "ListEmails")})
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private Pop3Action pop3Action;

  @Override
  public Action getProtocolAction() {
    return pop3Action;
  }
}
