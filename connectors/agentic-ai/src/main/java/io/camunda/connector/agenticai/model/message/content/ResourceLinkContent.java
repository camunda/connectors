/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Represents a resource link from MCP tool calls.
 *
 * <p>A resource link is a reference to an external resource identified by a URI. This matches the
 * MCP specification's ResourceLink structure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceLinkContent(
    String uri,
    String name,
    String description,
    String mimeType,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements Content {

  public ResourceLinkContent {
    if (uri == null) {
      throw new IllegalArgumentException("URI cannot be null");
    }
  }
}
