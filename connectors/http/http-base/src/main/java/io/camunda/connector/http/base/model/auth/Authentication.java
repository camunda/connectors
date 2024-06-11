/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BasicAuthentication.class, name = BasicAuthentication.TYPE),
  @JsonSubTypes.Type(value = NoAuthentication.class, name = NoAuthentication.TYPE),
  @JsonSubTypes.Type(value = OAuthAuthentication.class, name = OAuthAuthentication.TYPE),
  @JsonSubTypes.Type(value = BearerAuthentication.class, name = BearerAuthentication.TYPE),
  @JsonSubTypes.Type(value = ApiKeyAuthentication.class, name = ApiKeyAuthentication.TYPE)
})
@TemplateDiscriminatorProperty(
    label = "Type",
    group = "authentication",
    name = "type",
    defaultValue = NoAuthentication.TYPE,
    description = "Choose the authentication type. Select 'None' if no authentication is necessary")
public sealed interface Authentication
    permits ApiKeyAuthentication,
        BasicAuthentication,
        BearerAuthentication,
        NoAuthentication,
        OAuthAuthentication {}
