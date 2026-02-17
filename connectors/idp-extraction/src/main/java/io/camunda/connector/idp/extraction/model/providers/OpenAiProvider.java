/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@TemplateSubType(id = "openai", label = "OpenAi API Specification Provider")
public final class OpenAiProvider implements ProviderConfig {

  @TemplateProperty(
      id = "openAiEndpoint",
      label = "OpenAI Spec Endpoint",
      group = "configuration",
      type = TemplateProperty.PropertyType.Text,
      description = "Specify the OpenAI compatible specification endpoint.",
      binding = @TemplateProperty.PropertyBinding(name = "openAiEndpoint"),
      feel = FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  private String openAiEndpoint;

  @FEEL
  @TemplateProperty(
      id = "openAiHeaders",
      label = "Headers",
      group = "configuration",
      description = "Map of HTTP headers to add to the request.",
      feel = FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  private Map<String, String> openAiHeaders;

  public String getOpenAiEndpoint() {
    return openAiEndpoint;
  }

  public void setOpenAiEndpoint(String openAiEndpoint) {
    this.openAiEndpoint = openAiEndpoint;
  }

  public Map<String, String> getOpenAiHeaders() {
    return openAiHeaders;
  }

  public void setOpenAiHeaders(Map<String, String> openAiHeaders) {
    this.openAiHeaders = openAiHeaders;
  }
}
