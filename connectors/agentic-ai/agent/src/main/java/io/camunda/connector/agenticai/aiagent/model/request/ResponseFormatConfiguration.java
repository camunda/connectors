/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = ResponseFormatConfiguration.TextResponseFormatConfiguration.class,
      name = "text"),
  @JsonSubTypes.Type(
      value = ResponseFormatConfiguration.JsonResponseFormatConfiguration.class,
      name = "json")
})
@TemplateDiscriminatorProperty(
    group = "response",
    label = "Response format",
    name = "type",
    description = "Specify the response format. Support for JSON mode varies by provider.",
    defaultValue = "text")
public sealed interface ResponseFormatConfiguration {
  @TemplateSubType(id = "text", label = "Text")
  record TextResponseFormatConfiguration(
      @TemplateProperty(
              group = "response",
              label = "Parse text as JSON",
              description = "Tries to parse the LLM response text as JSON object.",
              tooltip =
                  "Use this option in combination with models which don't support native JSON mode/structured tool calling (e.g. Anthropic). "
                      + "Make sure to instruct the model to return valid JSON in the system prompt. "
                      + "The parsed JSON will be available as <code>response.responseJson</code>.<br><br>"
                      + "If parsing fails, <code>null</code> will be returned as JSON response, but the text content "
                      + "will still be available as <code>response.responseText</code>.",
              type = TemplateProperty.PropertyType.Boolean,
              optional = true)
          Boolean parseJson)
      implements ResponseFormatConfiguration {}

  @TemplateSubType(id = "json", label = "JSON")
  record JsonResponseFormatConfiguration(
      @FEEL
          @TemplateProperty(
              group = "response",
              label = "Response JSON schema",
              description =
                  "An optional response <a href=\"https://json-schema.org/\" target=\"_blank\">JSON Schema</a> to instruct the "
                      + "model how to structure the JSON output.",
              tooltip =
                  "If supported by the model, the response will be structured according to the provided schema. A parsed "
                      + "version of the response will be available as <code>response.responseJson</code>.",
              feel = FeelMode.required,
              optional = true)
          Map<String, Object> schema,
      @FEEL
          @TemplateProperty(
              group = "response",
              label = "Response JSON schema name",
              description =
                  "An optional name for the response JSON Schema to make the model aware of the expected output.",
              feel = FeelMode.optional,
              defaultValue = "Response",
              optional = true)
          String schemaName)
      implements ResponseFormatConfiguration {}
}
