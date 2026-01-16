/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import io.camunda.connector.api.document.Document;

public sealed interface ResourceData {

  record BlobResourceData(String uri, String mimeType, byte[] blob) implements ResourceData {}

  record TextResourceData(String uri, String mimeType, String text) implements ResourceData {}

  record CamundaDocumentResourceData(String uri, String mimeType, Document document)
      implements ResourceData {}
}
