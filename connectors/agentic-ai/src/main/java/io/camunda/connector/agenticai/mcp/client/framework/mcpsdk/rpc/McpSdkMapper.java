/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import io.camunda.connector.agenticai.mcp.client.model.Annotations;
import io.camunda.connector.agenticai.mcp.client.model.Icon;
import java.util.List;

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

  /**
   * Maps MCP SDK Icons list to connector model Icons list.
   *
   * @param sdkIcons The SDK icons list to map, may be null
   * @return The mapped icons list, or null if input was null
   */
  static List<Icon> mapIcons(List<io.modelcontextprotocol.spec.McpSchema.Icon> sdkIcons) {
    if (sdkIcons == null) {
      return null;
    }
    return sdkIcons.stream().map(McpSdkMapper::mapIcon).toList();
  }

  /**
   * Maps a single MCP SDK Icon to connector model Icon.
   *
   * @param sdkIcon The SDK icon to map, may be null
   * @return The mapped icon, or null if input was null
   */
  static Icon mapIcon(io.modelcontextprotocol.spec.McpSchema.Icon sdkIcon) {
    if (sdkIcon == null) {
      return null;
    }
    return new Icon(sdkIcon.src(), sdkIcon.mimeType(), sdkIcon.sizes());
  }
}
