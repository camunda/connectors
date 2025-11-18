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
import io.camunda.connector.idp.extraction.request.common.DocumentRequestData;
import java.util.List;

public class ClassificationRequestData extends DocumentRequestData {
  @TemplateProperty(
      id = "converseData",
      label = "AWS Bedrock Converse Parameters",
      group = "input",
      type = TemplateProperty.PropertyType.Hidden,
      description = "Specify the parameters for AWS Bedrock",
      defaultValue = "= input.converseData",
      binding = @TemplateProperty.PropertyBinding(name = "converseData"),
      feel = Property.FeelMode.disabled)
  ConverseData converseData;

  @TemplateProperty(
      id = "documentTypes",
      label = "Document types",
      group = "input",
      type = TemplateProperty.PropertyType.Hidden,
      description = "The possible classification types considered by the model",
      defaultValue = "= input.documentTypes",
      binding = @TemplateProperty.PropertyBinding(name = "documentTypes"),
      feel = Property.FeelMode.disabled)
  List<String> documentTypes;

  @TemplateProperty(
      id = "userPrompt",
      label = "user prompt",
      group = "input",
      type = TemplateProperty.PropertyType.Hidden,
      description = "The user prompt for the model",
      defaultValue = "= input.userPrompt",
      binding = @TemplateProperty.PropertyBinding(name = "userPrompt"),
      feel = Property.FeelMode.disabled)
  String userPrompt;

  @TemplateProperty(
      id = "autoClassify",
      label = "Auto classify",
      group = "input",
      type = TemplateProperty.PropertyType.Hidden,
      description = "The model can classify as a type not in the list if confidence is high.",
      defaultValue = "= input.autoClassify",
      binding = @TemplateProperty.PropertyBinding(name = "autoClassify"),
      feel = Property.FeelMode.disabled)
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

  public String getUserPrompt() {
    return userPrompt;
  }

  public void setUserPrompt(String userPrompt) {
    this.userPrompt = userPrompt;
  }

  public boolean isAutoClassify() {
    return autoClassify;
  }

  public void setAutoClassify(boolean autoClassify) {
    this.autoClassify = autoClassify;
  }
}
