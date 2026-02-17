/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request.auth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@TemplateSubType(id = OAuthAuthentication.TYPE, label = "OAuth 2.0 (Microsoft Entra ID)")
public record OAuthAuthentication(
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The tenant id. Learn more in our <a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/azure-blob-storage/#oauth-20\">documentation</a>.",
            feel = FeelMode.optional)
        @NotBlank
        String tenantId,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The client if of the application. Learn more in our <a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/azure-blob-storage/#oauth-20\">documentation</a>.",
            feel = FeelMode.optional)
        @NotBlank
        String clientId,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The client secret of the application. Learn more in our <a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/azure-blob-storage/#oauth-20\">documentation</a>.",
            feel = FeelMode.optional)
        @NotBlank
        String clientSecret,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The account url of the storage account. Learn more in our <a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/azure-blob-storage/#oauth-20\">documentation</a>.",
            feel = FeelMode.optional)
        @NotBlank
        String accountUrl)
    implements Authentication {
  @TemplateProperty(ignore = true)
  public static final String TYPE = "oAuth-client-credentials-flow";
}
