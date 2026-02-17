/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers.azure;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public class AiFoundryConfig {

  @TemplateProperty(
      id = "usingOpenAI",
      label = "Model type",
      group = "configuration",
      type = Dropdown,
      description = "Specify if the Azure AI Foundry is using OpenAI",
      defaultValue = "false",
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      choices = {
        @TemplateProperty.DropdownPropertyChoice(
            label = "Base Azure Foundry model",
            value = "false"),
        @TemplateProperty.DropdownPropertyChoice(label = "Azure OpenAI model", value = "true")
      })
  boolean usingOpenAI;

  @TemplateProperty(
      id = "endpoint",
      label = "Azure AI Foundry Endpoint",
      group = "configuration",
      type = TemplateProperty.PropertyType.Text,
      description = "Specify the endpoint of the Azure AI Foundry",
      binding = @TemplateProperty.PropertyBinding(name = "endpoint"),
      feel = FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  private String endpoint;

  @TemplateProperty(
      id = "apiKey",
      label = "Azure AI Foundry API Key",
      group = "configuration",
      type = TemplateProperty.PropertyType.Text,
      description = "Specify the API key of the Azure AI Foundry",
      binding = @TemplateProperty.PropertyBinding(name = "apiKey"),
      feel = FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  private String apiKey;

  public boolean isUsingOpenAI() {
    return usingOpenAI;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setUsingOpenAI(boolean usingOpenAI) {
    this.usingOpenAI = usingOpenAI;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }
}
