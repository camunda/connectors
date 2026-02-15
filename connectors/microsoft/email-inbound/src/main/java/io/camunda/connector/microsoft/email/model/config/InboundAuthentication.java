/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.config;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record InboundAuthentication(
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "Tenant ID",
            tooltip =
                "Your Microsoft Entra (Azure AD) tenant ID. Find this in Azure Portal → Microsoft Entra ID → Overview. <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/microsoft-o365-mail-inbound/#authentication\" target=\"_blank\">Learn more</a>",
            feel = Property.FeelMode.optional)
        @NotBlank
        String tenantId,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "Client ID",
            tooltip =
                "The application (client) ID from your app registration. Find this in Azure Portal → App registrations → Your app → Overview. <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/microsoft-o365-mail-inbound/#authentication\" target=\"_blank\">Learn more</a>",
            feel = Property.FeelMode.optional)
        @NotBlank
        String clientId,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "Client Secret",
            tooltip =
                "The client secret value from your app registration. Create this in Azure Portal → App registrations → Your app → Certificates & secrets. <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/microsoft-o365-mail-inbound/#authentication\" target=\"_blank\">Learn more</a>",
            feel = Property.FeelMode.optional)
        @NotBlank
        String clientSecret) {}
