/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SimpleAuthentication.class, name = SimpleAuthentication.TYPE),
  @JsonSubTypes.Type(value = XOAuthAuthentication.class, name = XOAuthAuthentication.TYPE),
  @JsonSubTypes.Type(value = NoAuthentication.class, name = NoAuthentication.TYPE)
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "authentication",
    name = "type",
    defaultValue = "simple",
    description = "Specify the Email authentication strategy. None is only supported for SMTP.")
public sealed interface Authentication
    permits SimpleAuthentication, XOAuthAuthentication, NoAuthentication {}
