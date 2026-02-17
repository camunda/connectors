/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers.gcp;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;

public record GcpAuthentication(
    @TemplateProperty(
            id = "authType",
            label = "Type",
            group = "authentication",
            type = Dropdown,
            defaultValue = "refresh",
            constraints = @PropertyConstraints(notEmpty = true),
            choices = {
              @DropdownPropertyChoice(label = "Bearer token", value = "bearer"),
              @DropdownPropertyChoice(label = "Refresh token", value = "refresh"),
              @DropdownPropertyChoice(label = "Service account", value = "service_account")
            })
        GcpAuthenticationType authType,
    @TemplateProperty(
            id = "bearerToken",
            label = "Bearer token",
            description = "Enter a valid Google API Bearer token",
            group = "authentication",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "baseRequest.authentication.authType",
                    equals = "bearer"))
        String bearerToken,
    @TemplateProperty(
            id = "oauthClientId",
            label = "Client ID",
            description = "Enter Google API Client ID",
            group = "authentication",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "baseRequest.authentication.authType",
                    equals = "refresh"))
        String oauthClientId,
    @TemplateProperty(
            id = "oauthClientSecret",
            label = "Client secret",
            description = "Enter Google API client Secret",
            group = "authentication",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "baseRequest.authentication.authType",
                    equals = "refresh"))
        String oauthClientSecret,
    @TemplateProperty(
            id = "oauthRefreshToken",
            label = "Refresh token",
            description = "Enter a valid Google API refresh token",
            group = "authentication",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "baseRequest.authentication.authType",
                    equals = "refresh"))
        String oauthRefreshToken,
    @TemplateProperty(
            id = "serviceAccountJson",
            label = "Service account json",
            description = "Enter a the contents of your service account json file",
            group = "authentication",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "baseRequest.authentication.authType",
                    equals = "service_account"))
        String serviceAccountJson) {

  @Override
  public String toString() {
    return "Authentication{"
        + "authType="
        + authType
        + ", bearerToken=[REDACTED]"
        + ", oauthClientId=[REDACTED]"
        + ", oauthClientSecret=[REDACTED]"
        + ", oauthRefreshToken=[REDACTED]"
        + '}';
  }
}
