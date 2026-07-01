/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers.azure;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

/**
 * @deprecated Legacy IDP extraction provider model, used only by {@link
 *     io.camunda.connector.idp.extraction.ExtractionConnectorFunction} via {@code AzureProvider}.
 *     The structured / unstructured / classification connectors use the {@code request.common}
 *     model instead. Retained for backwards compatibility; no removal currently planned.
 */
@Deprecated(since = "8.9")
public class DocumentIntelligenceConfiguration {

  @TemplateProperty(
      id = "endpoint",
      label = "Azure Document Intelligence Endpoint",
      group = "configuration",
      type = TemplateProperty.PropertyType.Text,
      binding = @TemplateProperty.PropertyBinding(name = "endpoint"),
      feel = FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  private String endpoint;

  @TemplateProperty(
      id = "apiKey",
      label = "Azure Document Intelligence API Key",
      group = "configuration",
      type = TemplateProperty.PropertyType.Text,
      binding = @TemplateProperty.PropertyBinding(name = "apiKey"),
      feel = FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  private String apiKey;

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }
}
