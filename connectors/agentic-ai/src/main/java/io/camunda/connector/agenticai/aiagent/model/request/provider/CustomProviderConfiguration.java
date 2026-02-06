/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@TemplateSubType(id = CustomProviderConfiguration.CUSTOM_ID, label = "Custom Implementation (Self-Managed/Hybrid only)")
public record CustomProviderConfiguration(
    @FEEL
        @TemplateProperty(
            group = "provider",
            label = "Implementation type",
            type = TemplateProperty.PropertyType.String,
            feel = Property.FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotBlank
        String providerType,
    @FEEL
        @TemplateProperty(
            group = "provider",
            label = "Parameters",
            description = "Parameters for the custom model provider implementation.",
            feel = Property.FeelMode.required,
            optional = true)
        Map<String, Object> parameters)
    implements ProviderConfiguration {
  public static final String CUSTOM_ID = "custom";
}
