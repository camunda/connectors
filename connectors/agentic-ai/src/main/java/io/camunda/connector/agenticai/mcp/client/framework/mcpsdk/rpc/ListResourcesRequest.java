/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceDescription;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ListResourcesRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListResourcesRequest.class);

  private final String clientId;

  ListResourcesRequest(String clientId) {
    this.clientId = clientId;
  }

  public McpClientListResourcesResult execute(McpSyncClient client, AllowDenyList resourcesFilter) {
    LOGGER.debug("MCP({}): Executing list resources", clientId);

    var fetchedResources = client.listResources().resources();

    if (fetchedResources.isEmpty()) {
      LOGGER.debug("MCP({}): No resources found", clientId);
      return new McpClientListResourcesResult(Collections.emptyList());
    }

    final var filteredResources =
        fetchedResources.stream()
            .filter(resource -> resourcesFilter.isPassing(resource.uri()))
            .toList();

    if (filteredResources.isEmpty()) {
      LOGGER.debug(
          "MCP({}): No resources left after filtering. Filter: {}", clientId, resourcesFilter);
      return new McpClientListResourcesResult(Collections.emptyList());
    }

    var result =
        new McpClientListResourcesResult(
            filteredResources.stream()
                .map(
                    fr ->
                        new ResourceDescription(
                            fr.uri(),
                            fr.name(),
                            fr.description(),
                            fr.mimeType(),
                            McpSdkMapper.mapAnnotations(fr.annotations())))
                .toList());

    LOGGER.debug(
        "MCP({}): Resolved list of resources: {}",
        clientId,
        result.resources().stream().map(ResourceDescription::name).toList());

    return result;
  }
}
