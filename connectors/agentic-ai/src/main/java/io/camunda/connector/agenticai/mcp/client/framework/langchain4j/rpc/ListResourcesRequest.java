/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceDescription;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ListResourcesRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListResourcesRequest.class);

  public McpClientListResourcesResult execute(McpClient client, AllowDenyList resourcesFilter) {
    LOGGER.debug("MCP({}): Executing list resources", client.key());

    var fetchedResources = client.listResources();

    if (fetchedResources.isEmpty()) {
      LOGGER.debug("MCP({}): No resources found", client.key());
      return new McpClientListResourcesResult(Collections.emptyList());
    }

    final var filteredResources =
        fetchedResources.stream()
            .filter(resource -> resourcesFilter.isPassing(resource.uri()))
            .toList();

    if (filteredResources.isEmpty()) {
      LOGGER.debug(
          "MCP({}): No resources left after filtering. Filter: {}", client.key(), resourcesFilter);
      return new McpClientListResourcesResult(Collections.emptyList());
    }

    var result =
        new McpClientListResourcesResult(
            filteredResources.stream()
                .map(
                    fr ->
                        new ResourceDescription(
                            fr.uri(), fr.name(), fr.description(), fr.mimeType(), null))
                .toList());

    LOGGER.debug(
        "MCP({}): Resolved list of resources: {}",
        client.key(),
        result.resources().stream().map(ResourceDescription::name).toList());

    return result;
  }
}
