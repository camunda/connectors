/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request.auth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SASAuthentication.class, name = SASAuthentication.TYPE),
  @JsonSubTypes.Type(value = OAuthAuthentication.class, name = OAuthAuthentication.TYPE)
})
@TemplateDiscriminatorProperty(
    label = "Type",
    group = "authentication",
    name = "type",
    defaultValue = SASAuthentication.TYPE,
    description = "Choose the authentication type.")
public sealed interface Authentication permits SASAuthentication, OAuthAuthentication {}
