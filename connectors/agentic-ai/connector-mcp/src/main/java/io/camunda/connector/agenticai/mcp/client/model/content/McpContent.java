/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.content;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = McpTextContent.class, name = "text"),
  @JsonSubTypes.Type(value = McpBlobContent.class, name = "blob"),
  @JsonSubTypes.Type(value = McpObjectContent.class, name = "object"),
  @JsonSubTypes.Type(value = McpEmbeddedResourceContent.class, name = "resource"),
  @JsonSubTypes.Type(value = McpResourceLinkContent.class, name = "resource_link"),
  @JsonSubTypes.Type(value = McpDocumentContent.class, name = "document")
})
public sealed interface McpContent
    permits McpTextContent,
        McpBlobContent,
        McpObjectContent,
        McpEmbeddedResourceContent,
        McpResourceLinkContent,
        McpDocumentContent {
  Map<String, Object> metadata();
}
