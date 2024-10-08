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

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    defaultImpl = SimpleAuthentication.class)
@JsonSubTypes({@JsonSubTypes.Type(value = SimpleAuthentication.class, name = "simple")})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "authentication",
    name = "type",
    defaultValue = "simple",
    description = "Specify the Email authentication strategy.")
public sealed interface Authentication permits SimpleAuthentication {}
