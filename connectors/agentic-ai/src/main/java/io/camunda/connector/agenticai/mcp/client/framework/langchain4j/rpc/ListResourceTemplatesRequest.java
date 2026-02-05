/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceTemplate;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ListResourceTemplatesRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListResourceTemplatesRequest.class);

  public McpClientListResourceTemplatesResult execute(
      McpClient client, AllowDenyList resourcesFilter) {
    LOGGER.debug("MCP({}): Executing list resource templates", client.key());

    var fetchedResources = client.listResourceTemplates();

    if (fetchedResources.isEmpty()) {
      LOGGER.debug("MCP({}): No resource templates found", client.key());
      return new McpClientListResourceTemplatesResult(Collections.emptyList());
    }

    final var filteredResources =
        fetchedResources.stream()
            .filter(resource -> resourcesFilter.isPassing(resource.uriTemplate()))
            .toList();

    if (filteredResources.isEmpty()) {
      LOGGER.debug(
          "MCP({}): No resource templates left after filtering. Filter: {}",
          client.key(),
          resourcesFilter);
      return new McpClientListResourceTemplatesResult(Collections.emptyList());
    }

    var result =
        new McpClientListResourceTemplatesResult(
            filteredResources.stream()
                .map(
                    fr ->
                        new ResourceTemplate(
                            fr.uriTemplate(), fr.name(), fr.description(), fr.mimeType(), null, null, null))
                .toList());

    LOGGER.debug(
        "MCP({}): Resolved list of resource templates: {}",
        client.key(),
        result.resourceTemplates().stream().map(ResourceTemplate::name).toList());

    return result;
  }
}
