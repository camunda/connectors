/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@TemplateSubType(id = "sync", label = "Sync")
public record ComprehendSyncRequestData(
    @TemplateProperty(
            group = "input",
            label = "Text",
            description = "The document text to be analyzed.")
        @NotNull
        String text,
    @TemplateProperty(
            id = "sync.documentReadAction",
            label = "Document read action",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "TEXTRACT_DETECT_DOCUMENT_TEXT",
            feel = Property.FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "TEXTRACT_DETECT_DOCUMENT_TEXT",
                  label = "Detect document text"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "TEXTRACT_ANALYZE_DOCUMENT",
                  label = "Analyze document"),
              @TemplateProperty.DropdownPropertyChoice(value = "NO_DATA", label = "")
            },
            description =
                "Textract API operation that uses to extract text from PDF files and image files.")
        @NotNull
        ComprehendDocumentReadAction documentReadAction,
    @TemplateProperty(
            id = "sync.documentReadMode",
            label = "Document read mode",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "SERVICE_DEFAULT",
            feel = Property.FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "SERVICE_DEFAULT",
                  label = "Service default"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "FORCE_DOCUMENT_READ_ACTION",
                  label = "Force document read action"),
              @TemplateProperty.DropdownPropertyChoice(value = "NO_DATA", label = ""),
            },
            description = "Determines the text extraction actions for PDF files.")
        @NotNull
        ComprehendDocumentReadMode documentReadMode,
    @TemplateProperty(
            id = "sync.featureTypeTables",
            label = "Analyze tables",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "false")
        @NotNull
        boolean featureTypeTables,
    @TemplateProperty(
            id = "sync.featureTypeForms",
            label = "Analyze forms",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            defaultValue = "false")
        @NotNull
        boolean featureTypeForms,
    @TemplateProperty(
            group = "input",
            label = "Endpoint ARN",
            description = "The Amazon Resource Number (ARN) of the endpoint.")
        @NotNull
        String endpointArn)
    implements ComprehendRequestData {
  @Override
  public ComprehendDocumentReadAction getDocumentReadAction() {
    return documentReadAction;
  }

  @Override
  public ComprehendDocumentReadMode getDocumentReadMode() {
    return documentReadMode;
  }

  @Override
  public boolean getFeatureTypeTables() {
    return featureTypeTables;
  }

  @Override
  public boolean getFeatureTypeForms() {
    return featureTypeForms;
  }
}
