/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request.auth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = PasswordBasedAuthentication.class,
      name = "passwordBasedAuthentication"),
  @JsonSubTypes.Type(value = ApiKeyAuthentication.class, name = "apiKeyAuthentication"),
  @JsonSubTypes.Type(value = TokenBasedAuthentication.class, name = "tokenBasedAuthentication")
})
@TemplateDiscriminatorProperty(
    label = "Type",
    group = "authentication",
    name = "authentication.type",
    defaultValue = "passwordBasedAuthentication",
    description =
        "Choose the authentication type. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/automation-anywhere/\" target=\"_blank\">documentation</a>")
public sealed interface Authentication
    permits PasswordBasedAuthentication, ApiKeyAuthentication, TokenBasedAuthentication {}
