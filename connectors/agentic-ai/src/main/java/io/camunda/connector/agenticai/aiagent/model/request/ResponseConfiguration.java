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
            description = "Adds the full assistant message to the response.",
            tooltip =
                "In addition to the text content, the assistant message may include multiple additional content blocks "
                    + "and metadata (such as token usage). The message output will be available as <code>response.responseMessage</code>.",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "false")
        boolean includeAssistantMessage) {

  public ResponseConfiguration {
    if (format == null) {
      format = new TextResponseFormatConfiguration(true, false);
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
                label = "Include text output",
                description =
                    "Adds the first text output of the assistant message to the response.",
                tooltip =
                    "The text output will be available as <code>response.responseText</code>.",
                type = TemplateProperty.PropertyType.Boolean,
                defaultValueType = TemplateProperty.DefaultValueType.Boolean,
                defaultValue = "true")
            boolean includeText,
        @TemplateProperty(
                group = "response",
                label = "Parse the text response as JSON",
                description =
                    "Tries to parse the first text output of the assistant message as JSON object.",
                tooltip =
                    "Use this option in combination with models which don't support native JSON mode/structured tool calling (e.g. Anthropic). "
                        + "Make sure to instruct the model to return valid JSON in the system prompt. "
                        + "The parsed JSON will be available as <code>response.responseJson</code>. "
                        + "If parsing fails, null will be returned for the JSON reponse, but the text content will still be available if the option is enabled.",
                type = TemplateProperty.PropertyType.Boolean)
            boolean parseTextToJson)
        implements ResponseFormatConfiguration {}

    @TemplateSubType(id = "json", label = "JSON")
    record JsonResponseFormatConfiguration(
        @FEEL
            @TemplateProperty(
                group = "response",
                label = "Response JSON schema",
                description =
                    "An optional response <a href=\"https://json-schema.org/\" target=\"_blank\">JSON schema</a> to instruct the model how to structure the output.",
                tooltip =
                    "If supported by the model, the response will be structured according to the provided schema. A parsed version of the response will be available as <code>response.responseJson</code>.",
                feel = Property.FeelMode.required)
            Map<String, Object> schema,
        @FEEL
            @TemplateProperty(
                group = "response",
                label = "Response JSON schema name",
                description =
                    "An optional name for the response JSON schema to make the model aware of the expected output.",
                feel = Property.FeelMode.optional,
                defaultValue = "Response")
            String schemaName)
        implements ResponseFormatConfiguration {}
  }
}
