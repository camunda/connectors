/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.unstructured;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import io.camunda.connector.idp.extraction.request.common.DocumentRequestData;
import java.util.List;

public class UnstructuredExtractionRequestData extends DocumentRequestData {
  @TemplateProperty(
      id = "taxonomyItems",
      label = "Taxonomy Items",
      group = "input",
      type = TemplateProperty.PropertyType.Text,
      description = "Array of taxonomy items",
      defaultValue = "=[\n  {name: \"\", prompt: \"\"},\n  {name: \"\", prompt: \"\"}\n]",
      binding = @TemplateProperty.PropertyBinding(name = "taxonomyItems"),
      feel = FeelMode.optional)
  List<TaxonomyItem> taxonomyItems;

  @TemplateProperty(
      id = "converseData",
      label = "Ai converse parameters",
      group = "input",
      type = TemplateProperty.PropertyType.Text,
      description = "Specify the parameters for the ai conversation",
      defaultValue = "={\n  modelId: \"\",\n  temperature: 0.5,\n  topP: 0.9\n}",
      binding = @TemplateProperty.PropertyBinding(name = "converseData"),
      feel = FeelMode.optional)
  ConverseData converseData;

  public List<TaxonomyItem> getTaxonomyItems() {
    return taxonomyItems;
  }

  public void setTaxonomyItems(List<TaxonomyItem> taxonomyItems) {
    this.taxonomyItems = taxonomyItems;
  }

  public ConverseData getConverseData() {
    return converseData;
  }

  public void setConverseData(ConverseData converseData) {
    this.converseData = converseData;
  }
}
