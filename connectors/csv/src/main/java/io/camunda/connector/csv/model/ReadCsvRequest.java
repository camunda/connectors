/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv.model;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.TemplateDocumentProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record ReadCsvRequest(
    // Legacy input from element-template versions <= 2: raw CSV text or a document reference bound
    // directly to `data`. Kept (but hidden) so old templates and already-running instances keep
    // working under the new runtime. Superseded by the `document` input below.
    @TemplateProperty(ignore = true) Object data,
    @TemplateDocumentProperty(
            id = "document",
            binding = @TemplateProperty.PropertyBinding(name = "document"),
            tooltip = "The CSV document to read.")
        Document document,
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
