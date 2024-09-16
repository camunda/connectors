/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "simple", label = "Simple")
public record SimpleAuthentication(
    @TemplateProperty(
            group = "authentication",
            label = "Username",
            description =
                "Enter your full email address (e.g., user@example.com) or the username provided by your email service. This is used to authenticate your access to the mail server.",
            id = "simpleAuthenticationUsername")
        @NotBlank
        String username,
    @TemplateProperty(
            group = "authentication",
            label = "Email password",
            type = TemplateProperty.PropertyType.Hidden,
            feel = Property.FeelMode.disabled,
            description =
                "Enter the password associated with your email account. Keep your password secure and do not share it with others.",
            id = "simpleAuthenticationPassword")
        @NotBlank
        String password)
    implements Authentication {}
