/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.structured;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.idp.extraction.request.common.DocumentRequestData;
import java.util.List;
import java.util.Map;

public class StructuredExtractionRequestData extends DocumentRequestData {
  @TemplateProperty(
      id = "includedFields",
      label = "Included Fields",
      group = "input",
      type = TemplateProperty.PropertyType.Hidden,
      description = "List of fields that should be returned from the extraction",
      defaultValue = "= input.includedFields",
      binding = @TemplateProperty.PropertyBinding(name = "includedFields"),
      feel = Property.FeelMode.disabled)
  List<String> includedFields;

  @TemplateProperty(
      id = "renameMappings",
      label = "Rename mappings",
      group = "input",
      type = TemplateProperty.PropertyType.Hidden,
      description = "List of keys that should be renamed and not be given the default name",
      defaultValue = "= input.renameMappings",
      binding = @TemplateProperty.PropertyBinding(name = "renameMappings"),
      feel = Property.FeelMode.disabled)
  Map<String, String> renameMappings;

  @TemplateProperty(
      id = "delimiter",
      label = "delimiter",
      group = "input",
      type = TemplateProperty.PropertyType.Hidden,
      description = "The delimiter used for the variable name of the extracted field",
      defaultValue = "= input.delimiter",
      binding = @TemplateProperty.PropertyBinding(name = "delimiter"),
      feel = Property.FeelMode.disabled)
  String delimiter;

  public List<String> getIncludedFields() {
    return includedFields;
  }

  public void setIncludedFields(List<String> includedFields) {
    this.includedFields = includedFields;
  }

  public Map<String, String> getRenameMappings() {
    return renameMappings;
  }

  public void setRenameMappings(Map<String, String> renameMappings) {
    this.renameMappings = renameMappings;
  }

  public String getDelimiter() {
    return delimiter;
  }

  public void setDelimiter(String delimiter) {
    this.delimiter = delimiter;
  }
}
