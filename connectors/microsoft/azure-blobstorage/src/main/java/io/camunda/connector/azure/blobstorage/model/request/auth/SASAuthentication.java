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
@TemplateSubType(id = SASAuthentication.TYPE, label = "SAS token")
public record SASAuthentication(
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "SAS token",
            description =
                "Shared access signature (SAS) token of the container. Learn more in our <a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/azure-blob-storage/#prerequisites\">documentation</a>.",
            feel = FeelMode.optional)
        @NotBlank
        String SASToken,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "SAS URL",
            description =
                "Shared access signature (SAS) URL of the container. Learn more in our <a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/azure-blob-storage/#prerequisites\">documentation</a>.",
            feel = FeelMode.optional)
        @NotBlank
        String SASUrl)
    implements Authentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "SAS";
}
