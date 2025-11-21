/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

public record McpRemoteClientRequest(@Valid @NotNull McpRemoteClientRequestData data) {
  public record McpRemoteClientRequestData(
      @Valid @NotNull McpRemoteClientTransportConfiguration transport,
      @Valid McpRemoteClientOptionsConfiguration options,
      @Valid @NotNull McpConnectorModeConfiguration connectorMode,
      @Valid @Nullable McpClientToolsConfiguration tools) {}
}
