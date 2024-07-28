/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.model;

import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public record ComprehendRequestData(
    @TemplateProperty(
            label = "Execution type",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "ASYNC",
            feel = FeelMode.disabled,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "ASYNC", label = "Asynchronous"),
              @TemplateProperty.DropdownPropertyChoice(value = "SYNC", label = "Real-time")
            },
            description = "Endpoint inference type")
        @NotNull
        ComprehendExecutionType executionType,
    @TemplateProperty(
            group = "input",
            label = "Text",
            description = "The document text to be analyzed")
        @NotNull
        String text,
    @TemplateProperty(
            label = "Document read action",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "TEXTRACT_DETECT_DOCUMENT_TEXT",
            feel = FeelMode.disabled,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "TEXTRACT_DETECT_DOCUMENT_TEXT",
                  label = "Detect document text"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "TEXTRACT_ANALYZE_DOCUMENT",
                  label = "Analyze document")
            },
            description = "TODO")
        @NotNull
        ComprehendDocumentReadAction documentReadAction,
    @TemplateProperty(
            label = "Document read mode",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "SERVICE_DEFAULT",
            feel = FeelMode.disabled,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "SERVICE_DEFAULT",
                  label = "Default"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "FORCE_DOCUMENT_READ_ACTION",
                  label = "Force document read action")
            },
            description = "TODO")
        @NotNull
        ComprehendDocumentReadMode documentReadMode,
    @TemplateProperty(
            label = "Analyze tables",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean featureTypeTables,
    @TemplateProperty(
            label = "Analyze forms",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "true")
        @NotNull
        boolean featureTypeForms,
    @TemplateProperty(group = "input", label = "Endpoint ARN", description = "TODO") @NotNull
        String endpointArn) {}
