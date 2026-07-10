/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model.auth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ApiKeyAuthentication.class, name = ApiKeyAuthentication.TYPE),
  @JsonSubTypes.Type(value = OAuthAuthentication.class, name = OAuthAuthentication.TYPE)
})
@TemplateDiscriminatorProperty(
    label = "Authentication type",
    group = "authentication",
    name = "type",
    defaultValue = OAuthAuthentication.TYPE,
    description = "Choose how the connector authenticates to the App Integrations backend")
public sealed interface AppIntegrationsAuthentication
    permits ApiKeyAuthentication, OAuthAuthentication {}
