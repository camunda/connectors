/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;

public record Variables(
    @TemplateProperty(
            id = "variables.mode",
            label = "Template variable mode",
            group = "operationDetails",
            type = Dropdown,
            defaultValue = "simple",
            binding = @PropertyBinding(name = "variables.mode"),
            choices = {
              @DropdownPropertyChoice(label = "Key-value template replacements", value = "simple"),
              @DropdownPropertyChoice(
                  label = "Advanced Google Docs API requests",
                  value = "advanced")
            },
            condition = @PropertyCondition(property = "resource.type", equals = "file"))
        VariableMode mode,
    @TemplateProperty(
            id = "variables.replacements",
            label = "Template replacements",
            description =
                "Provide key-value pairs. For example, "
                    + "{\"customerName\": \"Acme Corp\"} replaces {{customerName}}.",
            group = "operationDetails",
            feel = FeelMode.required,
            binding = @PropertyBinding(name = "variables.replacements"),
            condition =
                @PropertyCondition(
                    property = "resource.template.variables.mode",
                    equals = "simple"))
        JsonNode replacements,
    @TemplateProperty(
            id = "variables.requests",
            label = "Template variables",
            description =
                "Advanced mode: provide a FEEL JSON object compatible with the Google Docs Requests API, "
                    + "for example {\"requests\": [...]}.",
            group = "operationDetails",
            feel = FeelMode.required,
            binding = @PropertyBinding(name = "variables.requests"),
            condition =
                @PropertyCondition(
                    property = "resource.template.variables.mode",
                    equals = "advanced"))
        JsonNode requests) {}
