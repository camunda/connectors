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
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = NoAuthentication.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SimpleAuthentication.class, name = "simple"),
  @JsonSubTypes.Type(value = NoAuthentication.class, name = "oauth"),
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "authentication",
    name = "type",
    defaultValue = "simple",
    description = "")
public sealed interface Authentication permits NoAuthentication, SimpleAuthentication {

  String getSender();

  Optional<String> getUser();

  Optional<String> getSecret();

  default boolean isSecuredAuth() {
    return getUser().isPresent() && getSecret().isPresent();
  }
}
