/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.auth;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "detailed", label = "Detailed")
public record DetailedAuthentication(
    @NotBlank @TemplateProperty(group = "authentication", label = "Host") String host,
    @NotBlank @TemplateProperty(group = "authentication", label = "Port") String port,
    @TemplateProperty(group = "authentication", label = "Username") String username,
    @TemplateProperty(group = "authentication", label = "Password") String password)
    implements JdbcAuthentication {}
