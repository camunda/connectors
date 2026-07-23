/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration.CUSTOM_ID;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * User-supplied chat model provider. The {@link #providerType()} discriminator is dispatched to a
 * {@code ChatModelFactory} bean the user implements and registers themselves (see {@code
 * ChatModelRegistry}); {@link #parameters()} is opaque configuration only that factory understands.
 */
@TemplateSubType(id = CUSTOM_ID, label = "Custom Implementation (Self-Managed/Hybrid only)")
public record CustomProviderConfiguration(
    @FEEL
        @TemplateProperty(
            group = "provider",
            label = "Provider type",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotBlank
        String providerType,
    @FEEL
        @TemplateProperty(
            group = "model",
            label = "Model",
            description = "Identifier of the model to use.",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotBlank
        String model,
    @FEEL
        @TemplateProperty(
            group = "provider",
            label = "Provider parameters",
            description = "Parameters for the custom chat model factory implementation.",
            feel = FeelMode.required,
            optional = true)
        Map<String, Object> parameters)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String CUSTOM_ID = "custom";

  @Override
  public String provider() {
    return providerType;
  }
}
