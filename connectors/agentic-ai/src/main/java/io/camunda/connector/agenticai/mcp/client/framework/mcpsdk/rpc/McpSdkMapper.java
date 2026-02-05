/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import io.camunda.connector.agenticai.mcp.client.model.result.Annotations;

/** Utility class for mapping MCP SDK types to connector model types. */
final class McpSdkMapper {

  private McpSdkMapper() {
    // Utility class
  }

  /**
   * Maps MCP SDK Annotations to connector model Annotations.
   *
   * @param sdkAnnotations The SDK annotations to map, may be null
   * @return The mapped annotations, or null if input was null
   */
  static Annotations mapAnnotations(
      io.modelcontextprotocol.spec.McpSchema.Annotations sdkAnnotations) {
    if (sdkAnnotations == null) {
      return null;
    }
    return new Annotations(
        sdkAnnotations.audience(), sdkAnnotations.priority(), sdkAnnotations.lastModified());
  }
}
