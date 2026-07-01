/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.idp.extraction.jackson.StringToMapDeserializer;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * @deprecated Legacy IDP extraction provider model, used only by {@link
 *     io.camunda.connector.idp.extraction.ExtractionConnectorFunction}. The structured /
 *     unstructured / classification connectors use the {@code request.common} provider model
 *     instead. Retained for backwards compatibility; no removal currently planned.
 */
@Deprecated(since = "8.9")
@TemplateSubType(id = "openai", label = "OpenAi API Specification Provider")
public final class OpenAiProvider implements ProviderConfig {

  @TemplateProperty(
      id = "openAiEndpoint",
      label = "OpenAI Spec Endpoint",
      group = "configuration",
      tooltip = "The OpenAI-compatible specification endpoint.",
      type = TemplateProperty.PropertyType.Text,
      binding = @TemplateProperty.PropertyBinding(name = "openAiEndpoint"),
      feel = FeelMode.disabled,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  private String openAiEndpoint;

  @JsonDeserialize(using = StringToMapDeserializer.class)
  @TemplateProperty(
      id = "openAiHeaders",
      label = "Headers",
      group = "configuration",
      tooltip = "Map of HTTP headers to add to the request.",
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
