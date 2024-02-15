/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "authType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = UriAuthentication.class, name = "uri"),
  @JsonSubTypes.Type(value = CredentialsAuthentication.class, name = "credentials")
})
@TemplateDiscriminatorProperty(
    label = "Connection type",
    group = "authentication",
    name = "authType",
    defaultValue = "uri")
public sealed interface RabbitMqAuthentication
    permits UriAuthentication, CredentialsAuthentication {}
