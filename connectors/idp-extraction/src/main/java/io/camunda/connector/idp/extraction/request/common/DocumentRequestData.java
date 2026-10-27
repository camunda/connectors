/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public class DocumentRequestData {
  @TemplateProperty(
      id = "document",
      label = "Document",
      group = "input",
      type = TemplateProperty.PropertyType.Text,
      description = "Specify the document",
      defaultValue = "=document",
      binding = @TemplateProperty.PropertyBinding(name = "document"),
      feel = FeelMode.optional,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  @NotNull
  Document document;

  public Document getDocument() {
    return document;
  }

  public void setDocument(Document document) {
    this.document = document;
  }
}
