/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Annotations provide metadata about resources as per MCP 2025-11-25 specification.
 *
 * <p>Annotations help clients determine how resources should be used, displayed, or prioritized.
 *
 * @param audience Intended audience(s) for the resource. Valid values: "user", "assistant"
 * @param priority Importance of the resource (0.0 to 1.0, where 1.0 is most important)
 * @param lastModified ISO 8601 timestamp indicating when the resource was last modified
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Annotations(List<String> audience, Double priority, String lastModified) {

  /** Role types for audience field */
  public static final String AUDIENCE_USER = "user";

  public static final String AUDIENCE_ASSISTANT = "assistant";
}
