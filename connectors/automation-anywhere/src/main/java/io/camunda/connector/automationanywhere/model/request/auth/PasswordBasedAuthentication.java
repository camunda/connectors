/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request.auth;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "passwordBasedAuthentication", label = "Authenticate (username and password)")
public record PasswordBasedAuthentication(
    @NotBlank
        @TemplateProperty(
            label = "Username",
            id = "passwordBassesUsername",
            group = "authentication")
        String username,
    @NotBlank @TemplateProperty(label = "Password", group = "authentication") String password,
    @NotNull
        @TemplateProperty(
            label = "Multiple login",
            group = "authentication",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "true", label = "TRUE"),
              @TemplateProperty.DropdownPropertyChoice(value = "false", label = "FALSE")
            })
        Boolean multipleLogin)
    implements Authentication {}
