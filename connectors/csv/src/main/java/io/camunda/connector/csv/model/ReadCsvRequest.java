/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record ReadCsvRequest(
    @TemplateProperty(
            label = "Data",
            tooltip = "CSV as a document or text",
            feel = FeelMode.optional)
        Object data,
    CsvFormat format,
    @TemplateProperty(
            label = "Row Type",
            tooltip = "Type of the row in the CSV file, either Object or Array",
            defaultValue = "Object")
        RowType rowType) {
  public enum RowType {
    Object,
    Array
  }
}
