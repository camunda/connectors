/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.extraction;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "abbyy", label = "ABBYY Vantage extractor")
public record AbbyyVantageExtractorRequest(
    @TemplateProperty(
            id = "abbyyBaseUrl",
            label = "ABBYY Vantage Base URL",
            group = "extractor",
            type = TemplateProperty.PropertyType.Text,
            description =
                "Base URL of the ABBYY Vantage instance (e.g., https://vantage-us.abbyy.com)",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String baseUrl,
    @TemplateProperty(
            id = "abbyyClientId",
            label = "Client ID",
            group = "extractor",
            type = TemplateProperty.PropertyType.Text,
            description = "OAuth2 Client ID from ABBYY Vantage Public API Client settings",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String clientId,
    @TemplateProperty(
            id = "abbyyClientSecret",
            label = "Client Secret",
            group = "extractor",
            type = TemplateProperty.PropertyType.Text,
            description = "OAuth2 Client Secret from ABBYY Vantage Public API Client settings",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String clientSecret,
    @TemplateProperty(
            id = "abbyySkillId",
            label = "Skill ID",
            group = "extractor",
            type = TemplateProperty.PropertyType.Text,
            description =
                "The ABBYY Vantage OCR skill ID (skill must be configured to output Text format)",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String skillId)
    implements ExtractionProvider {}
