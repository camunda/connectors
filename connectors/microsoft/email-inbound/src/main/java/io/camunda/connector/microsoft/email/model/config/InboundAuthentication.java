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
            description =
                "The tenant id. Learn more in our <a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/azure-blob-storage/#oauth-20\">documentation</a>.",
            feel = Property.FeelMode.optional)
        @NotBlank
        String tenantId,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The client if of the application. Learn more in our <a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/azure-blob-storage/#oauth-20\">documentation</a>.",
            feel = Property.FeelMode.optional)
        @NotBlank
        String clientId,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The client secret of the application. Learn more in our <a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/azure-blob-storage/#oauth-20\">documentation</a>.",
            feel = Property.FeelMode.optional)
        @NotBlank
        String clientSecret) {}
