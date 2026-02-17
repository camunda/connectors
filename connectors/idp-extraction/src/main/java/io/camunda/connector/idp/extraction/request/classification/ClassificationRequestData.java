/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.classification;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.idp.extraction.model.ConverseData;
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
      feel = FeelMode.optional)
  ConverseData converseData;

  @TemplateProperty(
      id = "documentTypes",
      label = "Document types",
      group = "input",
      type = TemplateProperty.PropertyType.Text,
      description = "The possible classification types considered by the model",
      defaultValue = "=[\n  \"\"\n]",
      binding = @TemplateProperty.PropertyBinding(name = "documentTypes"),
      feel = FeelMode.optional)
  List<String> documentTypes;

  @TemplateProperty(
      id = "autoClassify",
      label = "Auto classify",
      group = "input",
      type = TemplateProperty.PropertyType.Dropdown,
      description = "The model can classify as a type not in the list if confidence is high.",
      defaultValue = "false",
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      binding = @TemplateProperty.PropertyBinding(name = "autoClassify"),
      choices = {
        @TemplateProperty.DropdownPropertyChoice(label = "No", value = "false"),
        @TemplateProperty.DropdownPropertyChoice(label = "Yes", value = "true")
      },
      feel = FeelMode.disabled)
  boolean autoClassify;

  public ConverseData getConverseData() {
    return converseData;
  }

  public void setConverseData(ConverseData converseData) {
    this.converseData = converseData;
  }

  public List<String> getDocumentTypes() {
    return documentTypes;
  }

  public void setDocumentTypes(List<String> documentTypes) {
    this.documentTypes = documentTypes;
  }

  public boolean isAutoClassify() {
    return autoClassify;
  }

  public void setAutoClassify(boolean autoClassify) {
    this.autoClassify = autoClassify;
  }
}
