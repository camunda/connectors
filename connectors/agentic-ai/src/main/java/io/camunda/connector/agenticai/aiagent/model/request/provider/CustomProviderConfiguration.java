/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.CustomProviderConfiguration.CUSTOM_ID;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@TemplateSubType(id = CUSTOM_ID, label = "Custom Implementation (Hybrid/Self-Managed only)")
public record CustomProviderConfiguration(
    @FEEL
        @NotBlank
        @TemplateProperty(
            group = "provider",
            label = "Implementation type",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String providerType,
    @FEEL
        @TemplateProperty(
            group = "provider",
            label = "Parameters",
            description = "Parameters for the custom model provider implementation.",
            feel = FeelMode.required,
            optional = true)
        Map<String, Object> parameters)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String CUSTOM_ID = "custom";

  @Override
  public String providerId() {
    return providerType;
  }
}
