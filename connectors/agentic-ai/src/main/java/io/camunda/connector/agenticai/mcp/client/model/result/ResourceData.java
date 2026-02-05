/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.document.Document;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
  @com.fasterxml.jackson.annotation.JsonSubTypes.Type(ResourceData.BlobResourceData.class),
  @com.fasterxml.jackson.annotation.JsonSubTypes.Type(ResourceData.TextResourceData.class),
  @com.fasterxml.jackson.annotation.JsonSubTypes.Type(
      ResourceData.CamundaDocumentResourceData.class)
})
public sealed interface ResourceData {

  record BlobResourceData(String uri, String mimeType, byte[] blob, Annotations annotations)
      implements ResourceData {}

  record TextResourceData(String uri, String mimeType, String text, Annotations annotations)
      implements ResourceData {}

  record CamundaDocumentResourceData(
      String uri, String mimeType, Document document, Annotations annotations)
      implements ResourceData {}
}
