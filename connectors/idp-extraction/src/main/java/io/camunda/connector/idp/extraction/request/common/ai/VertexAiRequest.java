/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.ai;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;

@TemplateSubType(id = "vertexAi", label = "GCP VertexAi")
public record VertexAiRequest(
    @TemplateProperty(
            id = "authType",
            label = "Type",
            group = "ai",
            type = Dropdown,
            defaultValue = "refresh",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "Bearer token", value = "bearer"),
              @TemplateProperty.DropdownPropertyChoice(label = "Refresh token", value = "refresh"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Service account",
                  value = "service_account")
            })
        GcpAuthenticationType authType,
    @TemplateProperty(
            id = "bearerToken",
            label = "Bearer token",
            description = "Enter a valid Google API Bearer token",
            group = "ai",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(property = "ai.authType", equals = "bearer"))
        String bearerToken,
    @TemplateProperty(
            id = "oauthClientId",
            label = "Client ID",
            description = "Enter Google API Client ID",
            group = "ai",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(property = "ai.authType", equals = "refresh"))
        String oauthClientId,
    @TemplateProperty(
            id = "oauthClientSecret",
            label = "Client secret",
            description = "Enter Google API client Secret",
            group = "ai",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(property = "ai.authType", equals = "refresh"))
        String oauthClientSecret,
    @TemplateProperty(
            id = "oauthRefreshToken",
            label = "Refresh token",
            description = "Enter a valid Google API refresh token",
            group = "ai",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(property = "ai.authType", equals = "refresh"))
        String oauthRefreshToken,
    @TemplateProperty(
            id = "serviceAccountJson",
            label = "Service account json",
            description = "Enter a the contents of your service account json file",
            group = "ai",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "ai.authType",
                    equals = "service_account"))
        String serviceAccountJson,
    @TemplateProperty(group = "ai", id = "gcpRegion", label = "Region") String region,
    @TemplateProperty(group = "ai", id = "vertexProjectId", label = "Project ID") String projectId)
    implements AiProvider {}
