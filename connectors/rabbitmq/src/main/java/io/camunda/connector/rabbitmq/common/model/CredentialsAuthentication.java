/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "credentials", label = "Username/Password")
public record CredentialsAuthentication(
    @NotBlank @TemplateProperty(group = "authentication", label = "Username") String userName,
    @NotBlank @TemplateProperty(group = "authentication", label = "Password") String password)
    implements RabbitMqAuthentication {
  @Override
  public String toString() {
    return "CredentialsAuthentication{"
        + "userName='"
        + userName
        + "'"
        + ", password=[REDACTED]"
        + "}";
  }
}
