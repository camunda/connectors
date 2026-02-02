/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message.content;

import io.camunda.connector.api.document.Document;

/**
 * Represents an embedded resource blob that has been converted to a Camunda Document.
 *
 * <p>This is used internally when processing MCP tool call results that contain binary embedded
 * resources.
 */
public record EmbeddedResourceBlobDocumentContent(String uri, String mimeType, Document document)
    implements EmbeddedResourceContent.EmbeddedResource {
  public EmbeddedResourceBlobDocumentContent {
    if (uri == null) {
      throw new IllegalArgumentException("URI cannot be null");
    }
    if (document == null) {
      throw new IllegalArgumentException("Document cannot be null");
    }
  }
}
