/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpObjectContent(
    Object content, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements McpContent {
  public McpObjectContent {
    if (content == null) {
      throw new IllegalArgumentException("Content cannot be null");
    }
  }

  public static McpObjectContent objectContent(Object content) {
    return new McpObjectContent(content, null);
  }
}
