/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.classification;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.DocumentType;
import io.camunda.connector.idp.extraction.request.common.DocumentRequestData;
import java.util.List;

public class ClassificationRequestData extends DocumentRequestData {
  @TemplateProperty(
      id = "converseData",
      label = "AWS Bedrock Converse Parameters",
      group = "input",
      type = TemplateProperty.PropertyType.Text,
      description = "Specify the parameters for AWS Bedrock",
      defaultValue = "={\n  modelId: \"\",\n  temperature: 0.5,\n  topP: 0.9\n}",
      binding = @TemplateProperty.PropertyBinding(name = "converseData"),
      feel = Property.FeelMode.optional)
  ConverseData converseData;

  @TemplateProperty(
      id = "documentTypes",
      label = "Document types",
      group = "input",
      type = TemplateProperty.PropertyType.Text,
      description = "The possible classification types considered by the model",
      defaultValue =
          "=[\n  {\n    name: \"\",\n    classificationInstructions: \"\",\n    description: \"\",\n    outputValue: \"\"\n  }\n]",
      binding = @TemplateProperty.PropertyBinding(name = "documentTypes"),
      feel = Property.FeelMode.optional)
  List<DocumentType> documentTypes;

  @TemplateProperty(
      id = "fallbackOutputValue",
      label = "Fallback output value",
      group = "input",
      type = TemplateProperty.PropertyType.Text,
      description = "The value to return if the model has low confidence on all document types.",
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      binding = @TemplateProperty.PropertyBinding(name = "fallbackOutputValue"),
      feel = Property.FeelMode.optional)
  String fallbackOutputValue;

  public ConverseData getConverseData() {
    return converseData;
  }

  public void setConverseData(ConverseData converseData) {
    this.converseData = converseData;
  }

  public List<DocumentType> getDocumentTypes() {
    return documentTypes;
  }

  public void setDocumentTypes(List<DocumentType> documentTypes) {
    this.documentTypes = documentTypes;
  }

  public String getFallbackOutputValue() {
    return fallbackOutputValue;
  }

  public void setFallbackOutputValue(String fallbackOutputValue) {
    this.fallbackOutputValue = fallbackOutputValue;
  }
}
