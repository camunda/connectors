/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.generator.java.annotation.NestedProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

public record McpRemoteClientRequest(@Valid @NotNull McpRemoteClientRequestData data) {
  public record McpRemoteClientRequestData(
      @Valid @NotNull @NestedProperties(group = "transport")
          McpRemoteClientTransportConfiguration transport,
      @Valid @Nullable McpClientToolsConfiguration tools,
      @Valid @NotNull McpClientOperationConfiguration operation) {}
}
