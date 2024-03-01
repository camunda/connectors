/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.authentication;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BearerAuthentication.class, name = "token"),
  @JsonSubTypes.Type(value = ClientSecretAuthentication.class, name = "clientCredentials"),
  @JsonSubTypes.Type(value = RefreshTokenAuthentication.class, name = "refresh")
})
@TemplateDiscriminatorProperty(
    label = "Type",
    group = "authentication",
    name = "type",
    defaultValue = "refresh",
    description =
        "Authentication type depends on your MS Teams account permission and operation with connector. See <a href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/microsoft-teams/'>connector documentation</a>")
public sealed interface MSTeamsAuthentication
    permits BearerAuthentication, ClientSecretAuthentication, RefreshTokenAuthentication {}
