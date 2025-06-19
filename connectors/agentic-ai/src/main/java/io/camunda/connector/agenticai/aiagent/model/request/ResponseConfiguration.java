/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ResponseConfiguration(
    @Valid @NotNull ResponseFormatConfiguration format,
    @TemplateProperty(
            group = "response",
            label = "Include assistant message",
            description = "Include the full assistant message as part of the result object.",
            tooltip =
                "In addition to the text content, the assistant message may include multiple additional content blocks "
                    + "and metadata (such as token usage). The message will be available as <code>response.responseMessage</code>.",
            type = TemplateProperty.PropertyType.Boolean,
            optional = true)
        Boolean includeAssistantMessage) {

  public ResponseConfiguration {
    if (format == null) {
      format = new TextResponseFormatConfiguration(false);
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TextResponseFormatConfiguration.class, name = "text"),
    @JsonSubTypes.Type(value = JsonResponseFormatConfiguration.class, name = "json")
  })
  @TemplateDiscriminatorProperty(
      group = "response",
      label = "Response Format",
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
                label = "Response JSON Schema",
                description =
                    "An optional response <a href=\"https://json-schema.org/\" target=\"_blank\">JSON Schema</a> to instruct the "
                        + "model how to structure the JSON output.",
                tooltip =
                    "If supported by the model, the response will be structured according to the provided schema. A parsed "
                        + "version of the response will be available as <code>response.responseJson</code>.",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, Object> schema,
        @FEEL
            @TemplateProperty(
                group = "response",
                label = "Response JSON Schema name",
                description =
                    "An optional name for the response JSON Schema to make the model aware of the expected output.",
                feel = Property.FeelMode.optional,
                defaultValue = "Response",
                optional = true)
            String schemaName)
        implements ResponseFormatConfiguration {}
  }
}
