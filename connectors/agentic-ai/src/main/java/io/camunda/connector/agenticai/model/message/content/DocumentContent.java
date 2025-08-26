/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.api.document.Document;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentContent(Document document) implements Content {
  public DocumentContent {
    if (document == null) {
      throw new IllegalArgumentException("Document cannot be null");
    }
  }

  public static DocumentContent documentContent(Document document) {
    return new DocumentContent(document);
  }
}
