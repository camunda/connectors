/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.unstructured;

import io.camunda.connector.generator.dsl.Property;
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
      type = TemplateProperty.PropertyType.Hidden,
      description = "Array of taxonomy items",
      defaultValue = "= input.taxonomyItems",
      binding = @TemplateProperty.PropertyBinding(name = "taxonomyItems"),
      feel = Property.FeelMode.disabled)
  List<TaxonomyItem> taxonomyItems;

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
