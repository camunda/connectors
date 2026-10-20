/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.model.part;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.api.document.Document;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record A2aDocumentPart(
    Document document,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements A2aPart {
  public A2aDocumentPart {
    if (document == null) {
      throw new IllegalArgumentException("Document cannot be null");
    }
  }

  public static A2aDocumentPart documentPart(Document document) {
    return new A2aDocumentPart(document, null);
  }
}
