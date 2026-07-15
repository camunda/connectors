/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.extraction;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;

@TemplateSubType(id = "documentAi", label = "GCP DocumentAI extractor")
public record DocumentAiExtractorRequest(
    @TemplateProperty(
            id = "authType",
            label = "Type",
            group = "extractor",
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
            group = "extractor",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "extractor.authType",
                    equals = "bearer"))
        String bearerToken,
    @TemplateProperty(
            id = "oauthClientId",
            label = "Client ID",
            group = "extractor",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "extractor.authType",
                    equals = "refresh"))
        String oauthClientId,
    @TemplateProperty(
            id = "oauthClientSecret",
            label = "Client secret",
            group = "extractor",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "extractor.authType",
                    equals = "refresh"))
        String oauthClientSecret,
    @TemplateProperty(
            id = "oauthRefreshToken",
            label = "Refresh token",
            group = "extractor",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "extractor.authType",
                    equals = "refresh"))
        String oauthRefreshToken,
    @TemplateProperty(
            id = "serviceAccountJson",
            label = "Service account json",
            tooltip = "Enter the contents of your service account JSON file",
            group = "extractor",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "extractor.authType",
                    equals = "service_account"))
        String serviceAccountJson,
    @TemplateProperty(
            group = "extractor",
            id = "documentAiRegion",
            label = "Region",
            tooltip =
                "Geographic location where the Document AI processor runs. EU = European Union, US = United States.",
            type = Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "EU", value = "eu"),
              @TemplateProperty.DropdownPropertyChoice(label = "US", value = "us")
            })
        String region,
    @TemplateProperty(group = "extractor", id = "projectId", label = "Project ID") String projectId,
    @TemplateProperty(
            group = "extractor",
            id = "processorId",
            label = "Processor ID",
            tooltip = "Processor used to parse the document")
        String processorId)
    implements ExtractionProvider {}
